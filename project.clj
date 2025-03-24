(defproject vectordb "0.1.0-SNAPSHOT"
  :description "Vector Database in Clojure"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/data.json "2.4.0"]
                 [ring/ring-core "1.9.6"]
                 [ring/ring-jetty-adapter "1.9.6"]
                 [ring/ring-defaults "0.3.4"]
                 [compojure "1.7.0"]
                 [ring/ring-json "0.5.1"]
                 [hiccup "1.0.5"]
                 [clj-http "3.12.3"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ch.qos.logback/logback-classic "1.4.7"]
                 [com.github.seancorfield/next.jdbc "1.3.883"]
                 [org.xerial/sqlite-jdbc "3.41.2.2"]
                 [org.duckdb/duckdb_jdbc "0.8.1"]
                 [org.clojure/math.numeric-tower "0.0.5"]]
  :main ^:skip-aot vectordb.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                        :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[org.clojure/test.check "1.1.1"]]}}
  :repl-options {:init-ns vectordb.core}) 