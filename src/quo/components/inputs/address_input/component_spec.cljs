(ns quo.components.inputs.address-input.component-spec
  (:require
    [quo.components.inputs.address-input.view :as address-input]
    [quo.foundations.colors :as colors]
    [react-native.clipboard :as clipboard]
    [test-helpers.component :as h]))

(def ens-regex #"^(?=.{5,255}$)([a-zA-Z0-9-]+\.)*[a-zA-Z0-9-]+\.[a-zA-Z]{2,}$")
(def address-regex #"^0x[a-fA-F0-9]{40}$")

(h/describe "Address input"
  (h/test "default render"
    (with-redefs [clipboard/get-string #(% "")]
      (h/render [address-input/address-input])
      (h/is-truthy (h/get-by-label-text :address-text-input))))

  (h/test "on focus with blur? false"
    (with-redefs [clipboard/get-string #(% "")]
      (h/render [address-input/address-input {:ens-regex ens-regex}])
      (h/fire-event :on-focus (h/get-by-label-text :address-text-input))
      (h/has-prop (h/get-by-label-text :address-text-input)
                  :placeholder-text-color
                  colors/neutral-30)))

  (h/test "on focus with blur? true"
    (with-redefs [clipboard/get-string #(% "")]
      (h/render [address-input/address-input
                 {:blur?     true
                  :ens-regex ens-regex}])
      (h/fire-event :on-focus (h/get-by-label-text :address-text-input))
      (h/has-prop (h/get-by-label-text :address-text-input)
                  :placeholder-text-color
                  colors/neutral-80-opa-20)))

  (h/test "default value is properly set"
    (let [default-value "default-value"]
      (with-redefs [clipboard/get-string #(% "")]
        (h/render [address-input/address-input
                   {:default-value default-value
                    :ens-regex     ens-regex}])
        (h/has-prop (h/get-by-label-text :address-text-input)
                    :value
                    default-value))))

  (h/test "clear icon is shown when input has text"
    (with-redefs [clipboard/get-string #(% "")]
      (h/render [address-input/address-input
                 {:default-value "default value"
                  :ens-regex     ens-regex}])
      (-> (h/wait-for #(h/get-by-label-text :clear-button-container))
          (.then #(h/is-truthy (h/get-by-label-text :clear-button))))))

  (h/test "on blur with text and blur? false"
    (with-redefs [clipboard/get-string #(% "")]
      (h/render [address-input/address-input
                 {:default-value "default value"
                  :ens-regex     ens-regex}])
      (-> (h/wait-for #(h/get-by-label-text :clear-button))
          (.then (fn []
                   (h/fire-event :on-focus (h/get-by-label-text :address-text-input))
                   (h/fire-event :on-blur (h/get-by-label-text :address-text-input))
                   (h/has-prop (h/get-by-label-text :address-text-input)
                               :placeholder-text-color
                               colors/neutral-30))))))

  (h/test "on blur with text blur? true"
    (with-redefs [clipboard/get-string #(% "")]
      (h/render [address-input/address-input
                 {:default-value "default value"
                  :blur?         true
                  :ens-regex     ens-regex}])
      (-> (h/wait-for #(h/get-by-label-text :clear-button))
          (.then (fn []
                   (h/fire-event :on-focus (h/get-by-label-text :address-text-input))
                   (h/fire-event :on-blur (h/get-by-label-text :address-text-input))
                   (h/has-prop (h/get-by-label-text :address-text-input)
                               :placeholder-text-color
                               colors/neutral-80-opa-20))))))

  (h/test "on blur with no text and blur? false"
    (with-redefs [clipboard/get-string #(% "")]
      (h/render [address-input/address-input {:ens-regex ens-regex}])
      (h/fire-event :on-focus (h/get-by-label-text :address-text-input))
      (h/fire-event :on-blur (h/get-by-label-text :address-text-input))
      (h/has-prop (h/get-by-label-text :address-text-input)
                  :placeholder-text-color
                  colors/neutral-40)))

  (h/test "on blur with no text blur? true"
    (with-redefs [clipboard/get-string #(% "")]
      (h/render [address-input/address-input
                 {:blur?     true
                  :ens-regex ens-regex}])
      (h/fire-event :on-focus (h/get-by-label-text :address-text-input))
      (h/fire-event :on-blur (h/get-by-label-text :address-text-input))
      (h/has-prop (h/get-by-label-text :address-text-input)
                  :placeholder-text-color
                  colors/neutral-80-opa-40)))

  (h/test "on-clear is called"
    (let [on-clear (h/mock-fn)]
      (with-redefs [clipboard/get-string #(% "")]
        (h/render [address-input/address-input
                   {:default-value "default value"
                    :on-clear      on-clear
                    :ens-regex     ens-regex}])
        (-> (h/wait-for #(h/get-by-label-text :clear-button))
            (.then (fn []
                     (h/fire-event :press (h/get-by-label-text :clear-button))
                     (h/was-called on-clear)))))))

  (h/test "on-focus is called"
    (let [on-focus (h/mock-fn)]
      (with-redefs [clipboard/get-string #(% "")]
        (h/render [address-input/address-input {:on-focus on-focus}])
        (h/fire-event :on-focus (h/get-by-label-text :address-text-input))
        (h/was-called on-focus))))

  (h/test "on-blur is called"
    (let [on-blur (h/mock-fn)]
      (with-redefs [clipboard/get-string #(% "")]
        (h/render [address-input/address-input
                   {:on-blur   on-blur
                    :ens-regex ens-regex}])
        (h/fire-event :on-blur (h/get-by-label-text :address-text-input))
        (h/was-called on-blur))))

  (h/test "on-scan is called"
    (let [on-scan (h/mock-fn)]
      (with-redefs [clipboard/get-string #(% "")]
        (h/render [address-input/address-input {:on-scan on-scan}])
        (-> (h/wait-for #(h/get-by-label-text :scan-button))
            (.then (fn []
                     (h/fire-event :press (h/get-by-label-text :scan-button))
                     (h/was-called on-scan)))))))

  (h/test "paste from clipboard"
    (let [clipboard "clipboard"]
      (with-redefs [clipboard/get-string #(% clipboard)]
        (h/render [address-input/address-input {:ens-regex ens-regex}])
        (h/is-truthy (h/query-by-label-text :paste-button))
        (h/fire-event :press (h/get-by-label-text :paste-button))
        (-> (h/wait-for #(h/get-by-label-text :clear-button))
            (.then (fn []
                     (h/has-prop (h/get-by-label-text :address-text-input)
                                 :value
                                 clipboard)))))))

  (h/test "ENS loading state and call on-detect-ens"
    (let [clipboard     "test.eth"
          on-detect-ens (h/mock-fn)]
      (with-redefs [clipboard/get-string #(% clipboard)]
        (h/render [address-input/address-input
                   {:on-detect-ens on-detect-ens
                    :ens-regex     ens-regex}])
        (h/is-truthy (h/query-by-label-text :paste-button))
        (h/fire-event :press (h/get-by-label-text :paste-button))
        (-> (h/wait-for #(h/is-falsy (h/query-by-label-text :clear-button)))
            (.then (fn []
                     (h/wait-for #(h/get-by-label-text :loading-button-container))))
            (.then #(h/was-called on-detect-ens))))))

  (h/test "ENS valid state and call on-detect-ens"
    (let [clipboard     "test.eth"
          on-detect-ens (h/mock-fn)]
      (with-redefs [clipboard/get-string #(% clipboard)]
        (h/render [address-input/address-input
                   {:on-detect-ens         on-detect-ens
                    :valid-ens-or-address? true
                    :ens-regex             ens-regex}])
        (h/is-truthy (h/query-by-label-text :paste-button))
        (h/fire-event :press (h/get-by-label-text :paste-button))
        (-> (h/wait-for #(h/is-falsy (h/query-by-label-text :clear-button)))
            (.then (fn []
                     (h/wait-for #(h/get-by-label-text :positive-button-container))))
            (.then #(h/was-called on-detect-ens))))))

  (h/test "address loading state and call on-detect-address"
    (let [clipboard         "0x2f88d65f3cb52605a54a833ae118fb1363acccd2"
          on-detect-address (h/mock-fn)]
      (with-redefs [clipboard/get-string #(% clipboard)]
        (h/render [address-input/address-input
                   {:on-detect-address on-detect-address
                    :address-regex     address-regex}])
        (h/is-truthy (h/query-by-label-text :paste-button))
        (h/fire-event :press (h/get-by-label-text :paste-button))
        (-> (h/wait-for #(h/is-falsy (h/query-by-label-text :clear-button)))
            (.then (fn []
                     (h/wait-for #(h/get-by-label-text :loading-button-container))))
            (.then #(h/was-called on-detect-address))))))

  (h/test "address valid state and call on-detect-address"
    (let [clipboard         "0x2f88d65f3cb52605a54a833ae118fb1363acccd2"
          on-detect-address (h/mock-fn)]
      (with-redefs [clipboard/get-string #(% clipboard)]
        (h/render [address-input/address-input
                   {:on-detect-address     on-detect-address
                    :address-regex         address-regex
                    :valid-ens-or-address? true}])
        (h/is-truthy (h/query-by-label-text :paste-button))
        (h/fire-event :press (h/get-by-label-text :paste-button))
        (-> (h/wait-for #(h/is-falsy (h/query-by-label-text :clear-button)))
            (.then (fn []
                     (h/wait-for #(h/get-by-label-text :positive-button-container))))
            (.then #(h/was-called on-detect-address)))))))
