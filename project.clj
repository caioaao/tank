(defproject com.caioaao/tank "1.0.0"
  :description "Tank: Fault tolerant idioms"
  :url "https://github.com/caioaao/tank"
  :scm "https://github.com/caioaao/tank"
  :manifest {"GIT_COMMIT"   ~(System/getenv "GIT_COMMIT")
             "BUILD_NUMBER" ~(System/getenv "BUILD_NUMBER")}
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.7.559"]]
  :profiles {:source-paths ["dev"]
             :dev          {:dependencies [[org.clojure/test.check "0.10.0"]
                                           [nubank/matcher-combinators "1.3.1"]
                                           [com.gfredericks/test.chuck "0.2.10"]
                                           [org.clojure/tools.namespace "0.3.1"]]
                            :plugins      [[lein-cljfmt "0.6.6"]]
                            :global-vars  {*warn-on-reflection* true}}}
  :release-tasks [["deploy" "clojars"]]
  :deploy-repositories [["clojars" {:url           "https://clojars.org/repo"
                                    :sign-releases false
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password}]])
