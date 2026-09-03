(ns kanata-layer.core
  (:require
   [clojure.string :as string]
   [clojure.data.json :as json]
   [clojure.tools.cli :refer [parse-opts]]
   [nrepl.server :refer [start-server stop-server]]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [clojure.java.io :as io])
  (:import [java.net Socket InetAddress InetSocketAddress]
           [java.io OutputStreamWriter Writer])
  (:gen-class))

(def cli-options-spec
  [["-p" "--kanata-port PORT" "Port number"
    :default 1278
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]])

(defn ->waybarStatus [line]
  (let [new-layer
        (-> line
            (json/read-str :key-fn keyword)
            (get-in [:LayerChange :new])
            )]
    (if (some? new-layer)
         [(assoc {} "text" new-layer "alt" new-layer)]
         [])))

(defn -main
  [& args]
  (let [cli-opts (:options (parse-opts args cli-options-spec))]
    (with-open [messages-sock
                (Socket. (InetAddress/getLoopbackAddress) (:kanata-port cli-opts))
                out (OutputStreamWriter/new System/out)]
      (let [reader (io/reader messages-sock)]
        (->> #(.readLine reader)
             repeatedly
             (take-while some?)
             (mapcat #'->waybarStatus)
             (run! (fn [m]
                     (json/write m out)
                     (.write out (int \newline))
                     (.flush out))))))))
