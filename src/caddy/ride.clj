(ns caddy.ride
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]))


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


(defn set-file-server
  [file-path server-route]
  (set-config "http://localhost:2019/id/main-handler/routes/"
              {:content-type :json
               :body (json/write-str {"@id" (str (subs server-route 1) "-rewrite"),
                                      "group" "group0",
                                      "handle" 
                                      [{"handler" "rewrite",
                                        "uri" (str server-route "/")}],
                                      "match" [{"path" [server-route]}]})})
  (set-config "http://localhost:2019/id/main-handler/routes/"
              {:content-type :json
               :body (json/write-str {"@id" (subs server-route 1),
                                      "handle"
                                      [{"handler" "subroute",
                                        "routes"
                                        [{"handle" [{"handler" "vars", "root" file-path}]}
                                         {"handle" [{"handler" "rewrite", "strip_path_prefix" server-route}]}
                                         {"handle" [{"handler" "rewrite", "uri" "{http.matchers.file.relative}"}],
                                          "match" [{"file" {"try_files" ["{http.request.uri.path}" "/index.html"]}}]}
                                         {"handle" [{"handler" "file_server", "hide" ["./Caddyfile"]}]}]}],
                                      "match" [{"path" [(str server-route "/*")]}]})}))


(defn delete-route
  [server-route]
  (http/delete (str "http://localhost:2019/id/" (subs server-route 1) "-rewrite"))
  (http/delete (str "http://localhost:2019/id/" (subs server-route 1))))



;; CONFIG FETCHING
(comment
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
                                                                         [{"@id" "main-handler",
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
                                                                                                                    "handle" [{"handler" "reverse_proxy", "upstreams" [{"dial" "localhost:8080"}]}]})})

  (json/read-str ((http/get "http://localhost:2019/config/apps/http/servers/srv0") :body))
  (json/read-str ((http/get "http://localhost:2019/id/test_id") :body))
  (json/read-str ((http/get "http://localhost:2019/id/test_id/listen") :body))
  )

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