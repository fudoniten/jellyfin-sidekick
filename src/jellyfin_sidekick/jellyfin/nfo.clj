(ns jellyfin-sidekick.jellyfin.nfo
  "NFO file generation for Jellyfin metadata"
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn- determine-nfo-path
  "Determine the NFO file path for a media item.
   For movies: /path/to/Movie (2010)/Movie (2010).nfo
   For episodes: /path/to/Show/Season 01/S01E01.nfo"
  [item]
  (let [media-path (:Path item)
        item-type (:Type item)]
    (when media-path
      (let [dir (-> (io/file media-path) .getParent)
            filename (-> (io/file media-path) .getName)
            base-name (str/replace filename #"\.\w+$" "") ; Remove extension
            nfo-path (str dir "/" base-name ".nfo")]
        nfo-path))))

(defn- read-existing-nfo
  "Read existing NFO file if it exists, return parsed XML or nil"
  [nfo-path]
  (when (.exists (io/file nfo-path))
    (try
      (with-open [reader (io/reader nfo-path)]
        (xml/parse reader))
      (catch Exception e
        (log/warn e "Failed to read existing NFO, will create new" {:path nfo-path})
        nil))))

(defn- merge-tags-into-nfo
  "Merge new tags into existing NFO structure, preserving other metadata"
  [existing-nfo tags item-type]
  (if existing-nfo
    ;; Update existing NFO
    (let [root-tag (:tag existing-nfo)
          existing-content (:content existing-nfo)
          ;; Filter out existing tag elements
          other-elements (filter #(not= :tag (:tag %)) existing-content)
          ;; Create new tag elements
          tag-elements (map (fn [tag] {:tag :tag :content [tag]}) tags)]
      {:tag root-tag
       :content (concat other-elements tag-elements)})
    ;; Create new NFO
    (let [root-tag (case item-type
                    "Movie" :movie
                    "Episode" :episodedetails
                    :movie)]
      {:tag root-tag
       :content (map (fn [tag] {:tag :tag :content [tag]}) tags)})))

(defn write-tags-to-nfo!
  "Write tags to an NFO file for the given item.
   Preserves existing metadata, only updates tags."
  [item tags]
  (try
    (let [nfo-path (determine-nfo-path item)]
      (if nfo-path
        (do
          (log/info "Writing NFO file" {:path nfo-path :tags tags})
          
          ;; Read existing NFO if it exists
          (let [existing-nfo (read-existing-nfo nfo-path)
                updated-nfo (merge-tags-into-nfo existing-nfo tags (:Type item))]
            
            ;; Write NFO file
            (with-open [writer (io/writer nfo-path)]
              (xml/emit updated-nfo writer :encoding "UTF-8"))
            
            (log/info "Successfully wrote NFO file" {:path nfo-path})
            {:success true :nfo-path nfo-path}))
        (do
          (log/error "Could not determine NFO path for item" {:item item})
          {:success false :error "Could not determine NFO file path"})))
    (catch Exception e
      (log/error e "Error writing NFO file" {:item (:Id item)})
      {:success false :error (.getMessage e)})))
