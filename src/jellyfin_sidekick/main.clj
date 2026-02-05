(ns jellyfin-sidekick.main
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [jellyfin-sidekick.config :as config]
            [jellyfin-sidekick.system :as system]))

(def cli-options
  [["-c" "--config PATH" "Path to configuration EDN file"
    :multi true
    :default ["resources/config.edn"]
    :update-fn (fnil conj [])
    :validate [#(.exists (io/file %)) "config file not found"]]
   ["-l" "--log-level LEVEL" "Log level (trace, debug, info, warn, error)"
    :default nil
    :parse-fn keyword
    :validate [#{:trace :debug :info :warn :error} "must be one of: trace, debug, info, warn, error"]]
   ["-h" "--help"]])

(defn- usage [options-summary]
  (->> ["Jellyfin Sidekick"
        ""
        "A service to write NFO tags and trigger Jellyfin refreshes"
        ""
        "Usage: jellyfin-sidekick [options]"
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn deep-merge
  ([] {})
  ([a] a)
  ([a b] (if (and (map? a) (map? b))
           (merge-with deep-merge a b)
           b))
  ([a b & etc] (reduce deep-merge (deep-merge a b) etc)))

(defn merge-configs
  [configs]
  (apply deep-merge
         (map config/load-config
              configs)))

(defn -main [& args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (do (println (usage summary))
          (System/exit 0))

      (seq errors)
      (do (binding [*out* *err*]
            (println "Error parsing command line options:")
            (doseq [err errors] (println "  " err))
            (println)
            (println (usage summary)))
          (System/exit 1))

      :else
      (let [;; Only load config files if they were provided and exist
            config-map    (cond-> (if (seq (:config options))
                                   (merge-configs (:config options))
                                   ;; No config files - create minimal config from env vars
                                   {:log-level (keyword (or (System/getenv "LOG_LEVEL") "info"))
                                    :server {:port (Integer/parseInt (or (System/getenv "PORT") "8080"))}
                                    :jellyfin {:base-url (or (System/getenv "JELLYFIN_URL") "http://localhost:8096")
                                              :api-key (System/getenv "JELLYFIN_API_KEY")}})
                            (:log-level options) (assoc :log-level (:log-level options)))
            system-config (config/config->system config-map)
            system        (system/start system-config)]
        (log/info "Jellyfin Sidekick started" {:port (get-in config-map [:server :port])})
        (.addShutdownHook (Runtime/getRuntime)
                          (Thread. (fn []
                                     (log/info "Shutdown requested")
                                     (system/stop system))))
        (log/info "Blocking main thread; press Ctrl+C to exit")
        (deref (promise))))))
