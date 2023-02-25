(ns build
  (:require [babashka.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.tools.build.api :as b])
  (:import (java.io File)))

(defn parse-opts [args]
  (cli/parse-opts
    args {:alias {:a :aliases} :coerce {:aliases [:keyword]}}))

(defn gen-basis [args]
  (let [aliases (parse-opts args)]
    (b/create-basis
      {:project "deps.edn"
       :aliases (:aliases aliases)})))

(def lib 'com.github.zjjfly/template)
(def group-id (namespace lib))
(def artifact-id (name lib))
;(def version (format "1.2.%s" (b/git-count-revs nil)))
(def version "1.0")
(def clj-source (str "src/clj/" artifact-id))
(def java-source (str "src/java/" artifact-id))
(def resources "src/resources")
(def test-clj-source (str "test/clj/" artifact-id))
(def test-java-source (str "test/java/" artifact-id))
(def test-resources "test/resources")
(def target-dir "target")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" artifact-id version))
(def uber-file (format "target/%s-%s-standalone.jar" artifact-id version))

(defn java-file-exist?
  [dir]
  (let [files (file-seq (io/file dir))]
    (some #(s/ends-with? (.getName %) ".java")
          (filter #(.isFile %) files))))

(defn init
  "init project structure,create necessary directories"
  [_]
  (let [source-dirs [clj-source java-source resources]
        test-dirs [test-clj-source test-java-source test-resources]
        all-dirs (concat source-dirs test-dirs)]
    (doseq [^File f (map io/file all-dirs)]
      (when (not (.exists f))
        (.mkdirs f)))))

(defn clean
  "Delete the build target directory"
  [_]
  (println "Cleanup...")
  (b/delete {:path target-dir}))

(defn prep
  "prepare for building"
  [args]
  (init args)
  (println "Writing pom.xml...")
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     (gen-basis args)
                :src-dirs  [java-source clj-source]})
  (println "Copying resources...")
  (b/copy-dir {:src-dirs   [resources]
               :target-dir class-dir}))

(defn compile-java
  "compile java source files"
  [args]
  (println "Compiling java sources...")
  (when (java-file-exist? java-source)
    (b/javac {:src-dirs   [java-source]
              :class-dir  class-dir
              :basis      (gen-basis args)
              :javac-opts ["-source" "8" "-target" "8"]})))

(defn compile-clj
  "compile clojure source files"
  [args]
  (println "Compiling clojure sources...")
  (b/compile-clj {:basis     (gen-basis args)
                  :src-dirs  [clj-source]
                  :class-dir class-dir}))

(defn compile-all
  "compile all source files"
  [args]
  (compile-java args)
  (compile-clj args))

(defn jar
  "package jar file"
  [args]
  (println args)
  (clean args)
  (prep args)
  (compile-all args)
  (println "Packaging jar...")
  (b/jar {:class-dir class-dir
          :jar-file  jar-file
          :basis     (gen-basis args)
          :main      nil}))

(defn uber
  "package uberjar file"
  [args]
  (clean args)
  (prep args)
  (compile-all args)
  (println "Packaging uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     (gen-basis args)
           :main (symbol (str artifact-id ".core"))}))

(defn install-local
  "install jar into local repository"
  [args]
  (jar args)
  (println "Installing...")
  (b/install {:class-dir class-dir
              :uber-file uber-file
              :basis     (gen-basis args)
              :lib       lib
              :jar-file  jar-file
              :version   "1.0"}))

(defn -main [cmd & args]
  (let [c (requiring-resolve (symbol (str "build/" cmd)))]
    (if (nil? c)
      (do (println (str "This command \"" cmd "\" is not supported"))
          (println "Supported commands:")
          (println "  init          -- initial project structure")
          (println "  clean         -- cleanup build outputs")
          (println "  prep          -- init & write pom.xml and copy resources to build path")
          (println "  compile-java  -- compile java sources")
          (println "  compile-clj   -- compile clojure sources")
          (println "  compile-all   -- compile java & clojure sources")
          (println "  jar           -- package jar file")
          (println "  uber          -- package uberjar file")
          (println "  install-local -- install package into local repository")
          (println "Supported Arguments:")
          (println "  --a --aliases -- aliases to apply")
          )
      (c args))))
