(ns jellyfin-sidekick.config
  "Configuration loading and system setup"
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(defn load-config
  "Load configuration from an EDN file using Aero"
  [path]
  (-> (io/file path)
      (aero/read-config)))

(defn- parse-port [port-str]
  (if (string? port-str)
    (Integer/parseInt port-str)
    port-str))

(defn config->system
  "Convert configuration map to Integrant system config"
  [config]
  {:jellyfin-sidekick/logger {:log-level (get config :log-level :info)}
   :jellyfin-sidekick/jellyfin-client {:base-url (get-in config [:jellyfin :base-url])
                                       :api-key (get-in config [:jellyfin :api-key])}
   :jellyfin-sidekick/http-server {:port (-> (get-in config [:server :port])
                                             (parse-port))
                                   :jellyfin-client (ig/ref :jellyfin-sidekick/jellyfin-client)
                                   :logger (ig/ref :jellyfin-sidekick/logger)}})
