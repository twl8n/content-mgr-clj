(ns build
  (:require [clojure.tools.build.api :as b]))

;; ns could be net.clojars.userid where userid is your unique userid?
(def lib 'twl/cmgr) ;; namespace/libname creates ./target/libname-0.1.0-SNAPSHOT.jar 
(def version "0.1.0") ;; or source from file, etc
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile '[cmgr.core]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'cmgr.core}))
