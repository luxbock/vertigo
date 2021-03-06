(ns vertigo.structs-test
  (:use
    clojure.test
    vertigo.test-utils)
  (:require
    [byte-streams :as bytes]
    [vertigo.core :as c]
    [vertigo.bytes :as b]
    [vertigo.primitives :as p]
    [vertigo.structs :as s]))

(def primitives
  [s/int8
   s/uint8
   s/int16
   s/int16-le
   s/int16-be
   s/uint16
   s/uint16-le
   s/uint16-be
   s/int32
   s/int32-le
   s/int32-be
   s/uint32
   s/uint32-le
   s/uint32-be
   s/int64
   s/int64-le
   s/int64-be
   s/uint64
   s/uint64-le
   s/uint64-be
   s/float32
   s/float32-le
   s/float64-be
   s/float64
   s/float64-le
   s/float64-be])

(deftest test-simple-roundtrips
  (doseq [typ primitives]
    (let [s (if (re-find #"float" (name typ))
              (map double (range 10))
              (range 10))]
      (is (= s (c/marshal-seq typ s)))
      (is (= s (c/lazily-marshal-seq typ s)))
      (is (= s (c/lazily-marshal-seq typ s {:chunk-size 32}))))))

(defn temp-file []
  (doto (java.io.File/createTempFile "vertigo" ".tmp")
    (.deleteOnExit)))

(deftest test-format-roundtrips
  (let [s (c/marshal-seq s/int64 (range 1e5))]
    (is (= s (->> s bytes/to-byte-buffer (c/wrap s/int64))))
    (is (= s (->> s bytes/to-readable-channel (c/wrap s/int64)))))

  (let [f (temp-file)
        s (c/marshal-seq s/int64 (range 1e5))]
    (bytes/transfer s f)
    (is (= s (c/wrap s/int64 f)))))

(s/def-typed-struct tuple
  :x s/int32
  :y (s/array s/int64 10))

(deftest test-typed-struct
  (let [s (map
            #(hash-map :x %1 :y %2)
            (range 10)
            (repeat 10 (range 10)))
        ^:tuple ms (c/marshal-seq tuple s)]

    (is (= s ms))
    (is (= 0
          (c/get-in ms [0 :x])
          (c/get-in ms [0 :y 0])
          (get-in ms [0 :y 0])
          (get-in ms [0 :x])
          (let [idx 0]
            (c/get-in ms [idx :y idx]))))

    (is (thrown? IndexOutOfBoundsException
          (let [n 11]
            (prn (c/get-in ms [0 :y n])))))

    (is (= (range 10)
          (c/get-in ms [0 :y])
          (get-in ms [0 :y])))

    (is (= {:x 1 :y (range 10)}
          (c/get-in ms [1])
          (get-in ms [1])
          (nth ms 1))))

  (let [s (map
            #(hash-map :x %1 :y %2)
            (range 1)
            (repeat 1 (range 10)))
        ^:tuple ms (c/marshal-seq tuple s)
        n 0]

    (c/update-in! ms [0 :x] p/inc)
    (is (= 1 (get-in ms [0 :x]) (c/get-in ms [0 :x]) (c/get-in ms [n :x])))
    (c/set-in! ms [0 :x] 10)
    (is (= 10 (get-in ms [0 :x]) (c/get-in ms [0 :x]) (c/get-in ms [n :x])))))

;;;

(deftest ^:benchmark benchmark-reduce
  (let [s (take 1e6 (range 1e6))]
    (bench "reduce unchunked seq"
      (reduce max s)))
  (let [s (range 1e6)]
    (bench "reduce chunked seq"
      (reduce max s)))
  (let [s (vec (range 1e6))]
    (bench "reduce vector"
      (reduce max s)))
  (let [s (long-array 1e6)]
    (bench "reduce array"
      (reduce max s)))
  (let [s (c/marshal-seq s/int64 (range 1e6))]
    (bench "reduce int64 byte-seq"
      (reduce max s)))
  (let [s (c/marshal-seq s/int32 (range 1e6))]
    (bench "reduce int32 byte-seq"
      (reduce max s))))

(deftest ^:benchmark benchmark-marshalling
  (let [s (vec (range 10))]
    (bench "marshal 10 bytes"
      (c/marshal-seq s/int8 s)))
  (let [s (vec (range 100))]
    (bench "marshal 100 bytes"
      (c/marshal-seq s/int8 s)))
  (let [s (vec (range 1000))]
    (bench "marshal 1000 shorts"
      (c/marshal-seq s/int16 s)))
  (let [s (range 10)]
    (bench "lazily marshal 10 bytes"
      (c/marshal-seq s/int8 s)))
  (let [s (range 100)]
    (bench "lazily marshal 100 bytes"
      (c/lazily-marshal-seq s/int8 s)))
  (let [s (range 1000)]
    (bench "lazily marshal 1000 shorts"
      (c/lazily-marshal-seq s/int16 s))))

(deftest ^:benchmark benchmark-accessors
  (let [s (map
            #(hash-map :x %1 :y %2)
            (range 1)
            (repeat 1 (range 10)))
        ^:tuple ms (c/marshal-seq tuple s)]

    (batch-bench "mutable update nested structure"
      (c/update-in! ms [0 :y 0] p/inc))
    (batch-bench "mutable get nested structure"
      (c/get-in ms [0 :y 0])
      nil)
    (batch-bench "mutable set nested structure"
      (c/set-in! ms [0 :y 0] 0)))

  (let [s [{:x 0 :y (vec (range 10))}]]

    (batch-bench "persistent update nested structure"
      (update-in s [0 :y 0] inc))
    (batch-bench "persistent get nested structure"
      (get-in s [0 :y 0] inc))
    (batch-bench "persistent set nested structure"
      (assoc-in s [0 :y 0] 0))))
