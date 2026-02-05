(ns jellyfin-sidekick.smoke-test
  (:require [clojure.test :refer :all]))

(deftest smoke-test
  (testing "Basic smoke test"
    (is (= 1 1))))
