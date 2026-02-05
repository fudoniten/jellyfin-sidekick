(ns jellyfin-sidekick.http.routes
  "HTTP routes for the Jellyfin Sidekick API"
  (:require [reitit.ring :as ring]
            [ring.util.response :refer [response status content-type]]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [jellyfin-sidekick.jellyfin.api :as jellyfin]
            [jellyfin-sidekick.jellyfin.nfo :as nfo]))

(defn- ok [body]
  (-> (response body)
      (status 200)
      (content-type "application/json")))

(defn- json-response [body status-code]
  (-> (response body)
      (status status-code)
      (content-type "application/json")))

(defn- parse-json-body [request]
  (try
    (when-let [body (:body request)]
      (json/parse-stream (clojure.java.io/reader body) true))
    (catch Exception e
      (log/error e "Failed to parse JSON body")
      nil)))

(defn- update-item-tags!
  "Update tags for a Jellyfin item by writing NFO and triggering refresh"
  [{:keys [jellyfin-client]} item-id tags]
  (try
    (log/info "Updating tags for item" {:item-id item-id :tags tags})
    
    ;; 1. Get item details from Jellyfin to find file path
    (if-let [item (jellyfin/get-item jellyfin-client item-id)]
      (do
        (log/debug "Retrieved item" {:item-id item-id :path (:Path item)})
        
        ;; 2. Write NFO file with tags
        (let [nfo-result (nfo/write-tags-to-nfo! item tags)]
          (if (:success nfo-result)
            (do
              ;; 3. Trigger Jellyfin refresh
              (let [refresh-result (jellyfin/refresh-item! jellyfin-client item-id)]
                (if (:success refresh-result)
                  (ok {:success true
                       :itemId item-id
                       :nfoPath (:nfo-path nfo-result)
                       :refreshed true})
                  (json-response {:success false
                                 :itemId item-id
                                 :nfoPath (:nfo-path nfo-result)
                                 :refreshed false
                                 :error (:error refresh-result)}
                                500))))
            (json-response {:success false
                           :itemId item-id
                           :error (:error nfo-result)}
                          500))))
      (json-response {:error "Item not found in Jellyfin"
                     :itemId item-id}
                    404))
    (catch Exception e
      (log/error e "Error updating item tags" {:item-id item-id})
      (json-response {:error (.getMessage e)} 500))))

(defn handler
  "Create the ring handler for the API"
  [{:keys [jellyfin-client]}]
  (let [router
        (ring/router
         [["/healthz" {:get (fn [_] (ok {:status "ok"}))}]
          ["/api"
           ["/items/:itemId/tags" {:post (fn [req]
                                           (let [item-id (get-in req [:path-params :itemId])
                                                 body (parse-json-body req)
                                                 tags (:tags body)]
                                             (if tags
                                               (update-item-tags!
                                                {:jellyfin-client jellyfin-client}
                                                item-id
                                                tags)
                                               (json-response {:error "Missing 'tags' field in request body"}
                                                             400))))}]]])]
    (ring/ring-handler router (ring/create-default-handler))))
