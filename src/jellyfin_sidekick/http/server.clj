(ns jellyfin-sidekick.http.server
  "HTTP server using Ring and Reitit"
  (:require [ring.adapter.jetty :as jetty]
            [jellyfin-sidekick.http.routes :as routes]
            [taoensso.timbre :as log]))

(defn start!
  "Start the HTTP server"
  [{:keys [port jellyfin-client]}]
  (let [handler (routes/handler {:jellyfin-client jellyfin-client})
        server (jetty/run-jetty handler
                                {:port port
                                 :join? false})]
    (log/info "HTTP server started" {:port port})
    server))

(defn stop!
  "Stop the HTTP server"
  [server]
  (when server
    (.stop server)
    (log/info "HTTP server stopped")))
