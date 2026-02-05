(ns jellyfin-sidekick.system
  "Integrant system initialization and lifecycle"
  (:require [integrant.core :as ig]
            [taoensso.timbre :as log]
            [jellyfin-sidekick.http.server :as http]
            [jellyfin-sidekick.jellyfin.api :as jellyfin]))

(defmethod ig/init-key :jellyfin-sidekick/logger [_ {:keys [log-level]}]
  (log/merge-config! {:min-level (or log-level :info)})
  (log/info "Logger initialized" {:log-level log-level})
  :ok)

(defmethod ig/halt-key! :jellyfin-sidekick/logger [_ _]
  (log/info "Logger shutdown"))

(defmethod ig/init-key :jellyfin-sidekick/jellyfin-client [_ config]
  (log/info "Jellyfin client initialized" {:base-url (:base-url config)})
  (jellyfin/create-client config))

(defmethod ig/halt-key! :jellyfin-sidekick/jellyfin-client [_ _]
  (log/info "Jellyfin client shutdown"))

(defmethod ig/init-key :jellyfin-sidekick/http-server [_ {:keys [port jellyfin-client logger]}]
  (http/start! {:port port
                :jellyfin-client jellyfin-client}))

(defmethod ig/halt-key! :jellyfin-sidekick/http-server [_ server]
  (http/stop! server))

(defn start
  "Start the system"
  [config]
  (ig/init config))

(defn stop
  "Stop the system"
  [system]
  (ig/halt! system))
