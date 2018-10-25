(defproject tank "0.1.1-SNAPSHOT"
  :description "Tank: Fault tolerant idioms"
  :url "https://github.com/caioaao/tank"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.10.0-alpha3"]
                                  [nubank/matcher-combinators "0.4.2"]]
                   :plugins      [[lein-cljfmt "0.6.1"]]}}
  :release-tasks [#_["deploy" "clojars"]
                  ["change" "version"
                   "leiningen.release/bump-version" "patch"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :deploy-repositories [["clojars" {:url           "https://clojars.org/repo"
                                    :sign-releases false
                                    :username      :env/clojars_username
                                    :password      :env/clojars_password}]])
