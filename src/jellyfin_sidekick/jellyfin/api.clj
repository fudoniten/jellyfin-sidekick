(ns jellyfin-sidekick.jellyfin.api
  "Jellyfin API client for getting item info and triggering refreshes"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [cemerick.url :as url]
            [taoensso.timbre :as log]))

(defn create-client
  "Create a Jellyfin client configuration"
  [config]
  config)

(defn- authenticated-request
  "Make an authenticated request to Jellyfin"
  [{:keys [api-key base-url]} method path & {:keys [body query-params]}]
  (when-not api-key
    (throw (ex-info "Jellyfin API key not configured" {:config-keys [:api-key]})))
  (let [url (str (url/url base-url path))
        opts (cond-> {:headers {"X-Emby-Token" api-key
                                "Content-Type" "application/json"}
                      :throw-exceptions false}
               body (assoc :body (json/generate-string body))
               query-params (assoc :query-params query-params))]
    (log/debug "Jellyfin request" {:method method :url url})
    (case method
      :get (http/get url opts)
      :post (http/post url opts)
      (throw (ex-info "Unsupported HTTP method" {:method method})))))

(defn- format-guid
  "Format a hex string as a UUID with dashes"
  [id-string]
  (let [cleaned (str/replace id-string #"-" "")]
    (if (= 32 (count cleaned))
      (format "%s-%s-%s-%s-%s"
              (subs cleaned 0 8)
              (subs cleaned 8 12)
              (subs cleaned 12 16)
              (subs cleaned 16 20)
              (subs cleaned 20 32))
      id-string)))

(defn get-item
  "Get item details from Jellyfin by searching for it"
  [client item-id]
  (let [guid (format-guid item-id)
        response (authenticated-request client :get "Items"
                                       :query-params {:Ids guid})]
    (if (= 200 (:status response))
      (when-let [result (json/parse-string (:body response) true)]
        (first (:Items result)))
      (do
        (log/error "Failed to get item from Jellyfin"
                  {:item-id item-id
                   :status (:status response)
                   :body (:body response)})
        nil))))

(defn refresh-item!
  "Trigger Jellyfin to refresh an item's metadata from NFO files"
  [client item-id]
  (try
    (let [guid (format-guid item-id)
          response (authenticated-request client :post (str "Items/" guid "/Refresh")
                                         :query-params {:Recursive false
                                                       :MetadataRefreshMode "FullRefresh"
                                                       :ImageRefreshMode "Default"
                                                       :ReplaceAllMetadata false
                                                       :ReplaceAllImages false})]
      (if (= 204 (:status response))
        (do
          (log/info "Successfully triggered refresh for item" {:item-id item-id})
          {:success true})
        (do
          (log/error "Failed to refresh item"
                    {:item-id item-id
                     :status (:status response)
                     :body (:body response)})
          {:success false
           :error (:body response)
           :status (:status response)})))
    (catch Exception e
      (log/error e "Error refreshing item" {:item-id item-id})
      {:success false
       :error (.getMessage e)})))
