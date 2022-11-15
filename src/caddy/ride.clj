(ns caddy.ride
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]))


;; CONFIG FETCHING
(comment
  (http/get "http://localhost:2019/config/")
  (->
   "http://localhost:2019/config/"
   http/get
   :body
   json/read-str)
  (http/post "http://localhost:2019/load" { :content-type :json
                                           :body (json/write-str {"apps"
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
                                                                       "terminal" true}]}}}}})})
  (http/post "http://localhost:2019/config/apps/http/servers/srv0/listen"
             { :content-type :json :body (json/write-str [":8080"])})
  (http/post "http://localhost:2019/config/apps/http/servers/srv0/listen"
             {:content-type :json :body (json/write-str ":8081")})
  (http/delete "http://localhost:2019/config/apps/http/servers/srv0/listen")
  (http/patch "http://localhost:2019/config/apps/http/servers/srv0/listen"
             {:content-type :json :body (json/write-str [":8080"])})
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
        {"/" {:match {:file {:root "/home/marko/www/"}}}}}}
      }})
  
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
         "terminal" true}]}}}}}
  
  
  )