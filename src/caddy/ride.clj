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
   json/read-str))

(comment
  (->
   "http://localhost:2019/a"
   http/get))

(comment
  (->
   "http://localhost:2019/b"
   http/get))

(comment
  (->
   "http://localhost:2019/load"
   http/post
   {:body (json/write-str {:apps {:http {:servers {:hello {:listen [":2015"]
                                                           :routes [{:handle [{:handler "static_response"
                                                                               :body "Testing load config from clojure"}]}]}}}}})}))

(comment
  (->
   "http://localhost:2019/stop"
   http/post))