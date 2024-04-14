(ns dev
  (:require
   nomis-electric-demo.main
   [hyperfiddle.electric :as e]
   #?(:clj [electric-starter-app.server-jetty :as jetty])
   #?(:clj [shadow.cljs.devtools.api :as shadow])
   #?(:clj [shadow.cljs.devtools.server :as shadow-server])
   #?(:clj [clojure.tools.logging :as log])))

;;;; ___________________________________________________________________________

(comment (-main)) ; repl entrypoint

#?(:clj ;; Server Entrypoint
   (do
     (def config
       {:host "0.0.0.0"
        :port 8081
        :resources-path "public/electric_starter_app"
        :manifest-path ; contains Electric compiled program's version so client and server stays in sync
        "public//electric_starter_app/js/manifest.edn"})

     (defn -main [& _args]
       (log/info "Starting Electric compiler and server...")

       (shadow-server/start!)
       (shadow/watch :dev)
       (comment (shadow-server/stop!))

       #_{:clj-kondo/ignore [:inline-def]}
       (def server (jetty/start-server!
                    (fn [ring-request]
                      (e/boot-server {} nomis-electric-demo.main/Main ring-request))
                    config))

       (comment (.stop server))
       )))

#?(:cljs ;; Client Entrypoint
   (do
     (def electric-entrypoint (e/boot-client {} nomis-electric-demo.main/Main nil))

     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor (electric-entrypoint
                      #(js/console.log "Reactor success:" %)
                      #(js/console.error "Reactor failure:" %))))

     #_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]} ; not working
     (defn ^:dev/before-load stop! []
       (when reactor (reactor)) ; stop the reactor
       (set! reactor nil))))
