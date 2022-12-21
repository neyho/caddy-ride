(ns caddy.ride
  (:require
   [clojure.string :as str]
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.java.shell :as shell]))


;; Ovo ovdje je varijabla gdje treba spremiti pokrenuti caddy proces
(def ^:private caddy-process
  (atom nil))

(defn started?
  [])

;; ako postoji vrijednost u caddy-process varijabli onda treba ugasitit taj proces
;; i ocistiti varijablu (reset! caddy-process nil)
(defn caddy-stop []
  (when (some? @caddy-process)
    (try
      (.exitValue caddy-process)
      (catch IllegalArgumentException _
        (.destroy @caddy-process)
        (reset! caddy-process nil)))))

(defn caddy-start
  []
  (when (some? @caddy-process)
    (caddy-stop))
  ;; ovdje ide poziv caddy procesa uz pomoc clojure.java.shell librarya
  ;; kad se proces uspjesno pokrene postavi u varijablu vrijednost procesa
  (reset! caddy-process (.exec (Runtime/getRuntime) "caddy run")))

(comment
  (shell/sh "ls")
  (def process-test (.exec (Runtime/getRuntime) "caddy run"))
  (.destroy process-test)
  (def testing (atom nil))
  @testing
  (reset! testing (.exec (Runtime/getRuntime) "ls"))
  (.destroy @caddy-process)

  (some? caddy-process)
  (some? @caddy-process)
  
  @caddy-process
  (caddy-start)
  (caddy-stop))

(defn set-config
  ([url config] (set-config :post url config))
  ([method url config]
   (try
     ((case method
        :patch http/patch
        :put http/put
        http/post)
      url config)
     (catch clojure.lang.ExceptionInfo e
       (let [{body :body :as response} (ex-data e)
             {:strs [error]} (json/read-str body)]
         (throw
          (ex-info (str "Configuration error.\n" error)
                   {:url url
                    :config config
                    :response response})))))))


(defn path->id [path]
  (str/replace path #"[\/]+" "."))


(defn generate-id [type path] (str type (path->id path)))

(defn root-url
  [{:keys [protocol port host]
    :or {host "localhost"
         port 2019
         protocol "http"}}]
  (format "%s://%s:%s/" protocol host port))

(defn with-root-url
  [options & path]
  (str (root-url options) (str/join "/" (map name path))))


(comment
  (with-root-url {:host "dev.eywaonline.com" :port 9000} "test")
  (with-root-url {:host "dev.eywaonline.com" :port 9000} "test" "")
  (with-root-url nil))


(defn initialize-server
  ([] (initialize-server nil))
  ([options]
   (set-config
    (with-root-url options "load")
    {:content-type :json
     :body (json/write-str {"apps"
                            {"http"
                             {"servers"
                              {"srv0"
                               {"listen" [":443"],
                                "routes"
                                [{"handle"
                                  [{"@id" "spa-handler",
                                    "handler" "subroute",
                                    "routes"
                                    []}],
                                  "match" [{"host" ["localhost"]}],
                                  "terminal" true}]}}}}})})))


(comment
  (with-root-url nil :id :neki-handler :routes))


(defn set-file-server
  ([directory path] (set-file-server nil directory path))
  ([options directory path]
   (let [rewrite-id (generate-id "rewrite" (path->id path))
         file-server-id (generate-id "file_server" (path->id path))]
     (set-config (with-root-url options :id :spa-handler :routes)
                 {:content-type :json
                  :body (json/write-str {"@id" rewrite-id,
                                         "group" "group0",
                                         "handle"
                                         [{"handler" "rewrite",
                                           "uri" (str path "/")}],
                                         "match" [{"path" [path]}]})})
     (set-config (with-root-url options :id :spa-handler :routes)
                 {:content-type :json
                  :body (json/write-str {"@id" file-server-id,
                                         "handle"
                                         [{"handler" "subroute",
                                           "routes"
                                           [{"handle" [{"handler" "vars", "root" directory}]}
                                            {"handle" [{"handler" "rewrite", "strip_path_prefix" path}]}
                                            {"handle" [{"handler" "rewrite", "uri" "{http.matchers.file.relative}"}],
                                             "match" [{"file" {"try_files" ["{http.request.uri.path}" "/index.html"]}}]}
                                            {"handle" [{"handler" "file_server", "hide" ["./Caddyfile"]}]}]}],
                                         "match" [{"path" [(str path "/*")]}]})}))))


(defn delete-route
  ([path] (delete-route nil path))
  ([options path]
   (http/delete (str (with-root-url options :id) (generate-id "rewrite" (path->id path))))
   (http/delete (str (with-root-url options :id) (generate-id "file_server" (path->id path))))))



(comment
  (caddy-start)
  (caddy-stop)
  (initialize-server)
  (set-file-server "./web/a" "/a")
  (set-file-server "./web/b" "/b")
  (set-file-server "./web/b" "/c")
  (set-file-server "./web/b" "/d")
  (set-file-server "./web/b" "/e")
  (delete-route "/b")
  (delete-route "/c")

  (http/get "http://localhost:2019/config/")
  (->
   "http://localhost:2019/config/"
   http/get
   :body
   json/read-str)

  ;; Config structure
  (set-config "http://localhost:2019/load" {:content-type :json
                                            :body (json/write-str {"apps"
                                                                   {"http"
                                                                    {"servers"
                                                                     {"srv0"
                                                                      {"listen" [":443"],
                                                                       "routes"
                                                                       [{"handle"
                                                                         [{"@id" "spa-handler",
                                                                           "handler" "subroute",
                                                                           "routes"
                                                                           []}],
                                                                         "match" [{"host" ["localhost"]}],
                                                                         "terminal" true}]}}}}})})

  (set-config "http://localhost:2019/config/apps/http/servers/srv0/routes/0/handle/0/routes/"
              {:content-type :json
               :body (json/write-str {"handle"
                                      [{"handler" "subroute",
                                        "routes"
                                        [{"handle" [{"handler" "vars", "root" "./web/a"}]}
                                         {"handle" [{"handler" "rewrite", "strip_path_prefix" "/a"}]}
                                         {"handle" [{"handler" "file_server", "hide" ["./Caddyfile"]}]}]}],
                                      "match" [{"path" ["/a*"]}]})})


  (http/delete "http://localhost:2019/config/apps/http/servers/srv0/routes/0/handle/1")
  (http/delete "http://localhost:2019/config/apps/http/servers/srv0/listen/0")
  (http/delete "http://localhost:2019/config/apps/http/servers/srv0/routes/0/")
  (set-config "http://localhost:2019/config/apps/http/servers/srv0/routes/"
              {:content-type :json
               :body (json/write-str {"match" [{"path" ["/a"]}]
                                      "handle"
                                      [{"handler" "file_server"
                                        "root" "./web/a"}]})})
  (set-config "http://localhost:2019/config/apps/http/servers/srv0/routes/"
              {:content-type :json
               :body (json/write-str {"handle"
                                      [{"handler" "subroute",
                                        "routes"
                                        [{"match" [{"path" ["/a"]}]
                                          "handle" [{"handler" "vars", "root" "./web/a"} {"handler" "file_server"}]}]}]})})
  (set-config "http://localhost:2019/config/apps/http/servers/srv0/routes/0/handle/0/routes"
              {:content-type :json
               :body (json/write-str {"match" [{"path" ["/b"]}]
                                      "handle" [{"handler" "vars", "root" "./web/b"} {"handler" "file_server"}]})})
  (http/post "http://localhost:2019/config/apps/http/servers/srv0/routes/"
             {:content-type :json
              :body (json/write-str {"handle"
                                     [{"handler" "file_server"
                                       "root" "./web"}]})})
  (http/post "http://localhost:2019/config/apps/http/servers/srv0/listen/"
             {:content-type :json
              :body (json/write-str ":80")})

  (http/post "http://localhost:2019/config/apps/http/servers/srv0/listen"
             {:content-type :json :body (json/write-str [":8080"])})
  (http/post "http://localhost:2019/config/apps/http/servers/srv0/listen"
             {:content-type :json :body (json/write-str ":80")})
  (http/delete "http://localhost:2019/config/apps/http/servers/srv0/listen")
  (http/patch "http://localhost:2019/config/apps/http/servers/srv0/listen"
              {:content-type :json :body (json/write-str [":8080"])})

  (http/post "http://localhost:2019/config/apps/http/servers/srv0/@id"
             {:content-type :json
              :body (json/write-str "test_id")})
  (http/patch "http://localhost:2019/id/test_id/listen"
              {:content-type :json
               :body (json/write-str [":8080"])})

  (json/read-str
   ((http/get "http://localhost:2019/config/apps/http/servers/srv0/routes/0/handle/0/routes")
    :body))

  (http/post "http://localhost:2019/config/apps/http/servers/srv0/routes/0/handle/0/routes" {:content-type :json
                                                                                             :body (json/write-str {"match" [{"path" ["/a"]}]
                                                                                                                    "handle" [{"handler" "reverse_proxy", "upstreams" [{"dial" "localhost:8080"}]}]})}))

(comment
  (->
   "http://localhost/a"
   http/get))


(comment
  (def example
    {:apps
     {:http
      {:servers
       {:listen ["localhost:8080"]
        :routes
        {"/" {:match {:file {:root "/home/marko/www/"}}}}}}}})

  {"apps"
   {"http"
    {"servers"
     {"srv0"
      {"listen" [":443"],
       "routes"
       [{"handle"
         [{"handler" "subroute",
           "routes"
           [{"handle" [{"handler" "vars", "root" "./web/"} {"handler" "file_server", "hide" ["./Caddyfile"]}]}]}],
         "match" [{"host" ["localhost"]}],
         "terminal" true}]}}}}})