(ns kanata-layer.core
  (:require
   [clojure.string :as string]
   [clojure.data.json :as json]
   [clojure.tools.cli :refer [parse-opts]]
   [nrepl.server :refer [start-server stop-server]]
   [cider.nrepl :refer (cider-nrepl-handler)]
   [clojure.java.io :as io])
  (:import [java.net Socket InetAddress InetSocketAddress]
           [java.lang Runtime Thread]
           [java.io OutputStreamWriter Writer])
  (:gen-class))

(defonce nrepl-server (atom nil))

(def cli-options-spec
  [["-p" "--kanata-port PORT" "Port number"
    :default 1278
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-r" "--repl-port NREPL-PORT" "Port to start the nrepl server"
    :default 1378
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]])

(defn start-nrepl! [port]
  (when-not @nrepl-server
    (reset! nrepl-server (start-server :port port :handler cider-nrepl-handler))
    (println "nRepl server started on" port)))

(defn stop-nrepl! []
  (when @nrepl-server
    (stop-server @nrepl-server)
    (reset! nrepl-server nil)
    (println "Stopped nrepl server")))

(defn ->waybarStatus [line]
  (-> line
      (json/read-str :key-fn keyword)
      (get-in [:LayerChange :new])
      (as-> new-layer
          (when new-layer
            (assoc {} "text" new-layer "alt" new-layer)))))

(defn -main
  [& args]
  (let [cli-opts (:options (parse-opts args cli-options-spec))
        runtime (Runtime/getRuntime)]
    (start-nrepl! (:repl-port cli-opts))
    (.addShutdownHook runtime (Thread. (fn []
                                         (stop-nrepl!))))
    (with-open [messages-sock
                (Socket. (InetAddress/getLoopbackAddress) (:kanata-port cli-opts))
                out (OutputStreamWriter/new System/out)]
      (let [reader (io/reader messages-sock)]
        (->> #(.readLine reader)
             repeatedly
             (take-while some?)
             (keep #'->waybarStatus)
             (run! (fn [m]
                     (json/write m out)
                     (.write out (int \newline))
                     (.flush out))))))))
