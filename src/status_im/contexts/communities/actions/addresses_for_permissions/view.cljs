(ns status-im.contexts.communities.actions.addresses-for-permissions.view
  (:require
    [clojure.string :as string]
    [quo.core :as quo]
    [react-native.core :as rn]
    [react-native.gesture :as gesture]
    [status-im.common.password-authentication.view :as password-authentication]
    [status-im.constants :as constants]
    [status-im.contexts.communities.actions.addresses-for-permissions.style :as style]
    [status-im.contexts.communities.actions.permissions-sheet.view :as permissions-sheet]
    [utils.i18n :as i18n]
    [utils.money :as money]
    [utils.re-frame :as rf]))

(defn- role-keyword
  [role]
  (condp = role
    constants/community-token-permission-become-token-owner  :token-owner
    constants/community-token-permission-become-token-master :token-master
    constants/community-token-permission-become-admin        :admin
    constants/community-token-permission-become-member       :member
    nil))

(defn- balances->components-props
  [balances images-by-symbol]
  (for [{:keys [amount decimals type name] sym :symbol :as balance} balances]
    (cond-> balance
      true
      (assoc :type
             (condp = type
               constants/community-token-type-erc20  :token
               constants/community-token-type-erc721 :collectible
               :token))

      (= type constants/community-token-type-erc721)
      (assoc :collectible-name    name
             :collectible-img-src (images-by-symbol sym))

      (= type constants/community-token-type-erc20)
      (assoc :amount        (str (money/token->unit amount decimals))
             :token         (:symbol balance)
             :token-img-src (images-by-symbol sym)))))

(defn- cancel-join-request-drawer
  [community-id]
  (let [{:keys [name logo color]} (rf/sub [:communities/for-context-tag community-id])
        request-id                (rf/sub [:communities/my-pending-request-to-join community-id])]
    [:<>
     [quo/drawer-top
      {:type                :context-tag
       :context-tag-type    :community
       :title               (i18n/label :t/cancel-request?)
       :community-name      name
       :community-logo      logo
       :customization-color color}]
     [rn/view {:style style/drawer-body}
      [quo/text (i18n/label :t/pending-join-request-farewell)]]
     [quo/bottom-actions
      {:actions          :two-actions

       :button-one-label (i18n/label :t/confirm-and-cancel)
       :button-one-props {:customization-color color
                          :on-press
                          (fn []
                            (rf/dispatch [:communities/addresses-for-permissions-cancel-request
                                          request-id]))}

       :button-two-label (i18n/label :t/cancel)
       :button-two-props {:type     :grey
                          :on-press #(rf/dispatch [:hide-bottom-sheet])}}]]))

(defn- confirm-discard-drawer
  [community-id]
  (let [{:keys [name logo color]} (rf/sub [:communities/for-context-tag community-id])]
    [:<>
     [quo/drawer-top
      {:type                :context-tag
       :context-tag-type    :community
       :title               (i18n/label :t/discard-changes?)
       :community-name      name
       :community-logo      logo
       :customization-color color}]
     [rn/view {:style style/drawer-body}
      [quo/text (i18n/label :t/all-changes-will-be-discarded)]]
     [quo/bottom-actions
      {:actions          :two-actions

       :button-one-label (i18n/label :t/discard)
       :button-one-props {:customization-color color
                          :on-press
                          (fn []
                            (rf/dispatch [:dismiss-modal :addresses-for-permissions])
                            (rf/dispatch [:hide-bottom-sheet]))}

       :button-two-label (i18n/label :t/cancel)
       :button-two-props {:type     :grey
                          :on-press #(rf/dispatch [:hide-bottom-sheet])}}]]))

(defn- leave-community-drawer
  [community-id outro-message]
  (let [{:keys [name logo color]} (rf/sub [:communities/for-context-tag community-id])]
    [:<>
     [quo/drawer-top
      {:type                :context-tag
       :context-tag-type    :community
       :title               (i18n/label :t/leave-community?)
       :community-name      name
       :community-logo      logo
       :customization-color color}]
     [rn/view {:style style/drawer-body}
      [quo/text
       (if (string/blank? outro-message)
         (i18n/label :t/leave-community-farewell)
         outro-message)]]
     [quo/bottom-actions
      {:actions          :two-actions

       :button-one-label (i18n/label :t/confirm-and-leave)
       :button-one-props {:customization-color color
                          :on-press
                          #(rf/dispatch [:communities/addresses-for-permissions-leave community-id])}

       :button-two-label (i18n/label :t/cancel)
       :button-two-props {:type     :grey
                          :on-press #(rf/dispatch [:hide-bottom-sheet])}}]]))

(defn- account-item
  [{:keys [customization-color address name emoji]} _ _
   [addresses-to-reveal set-addresses-to-reveal community-id community-color share-all-addresses?]]
  (let [balances         (rf/sub [:communities/permissioned-balances-by-address community-id address])
        images-by-symbol (rf/sub [:communities/token-images-by-symbol community-id])
        checked?         (contains? addresses-to-reveal address)
        toggle-address   (fn []
                           (let [new-addresses (if checked?
                                                 (disj addresses-to-reveal address)
                                                 (conj addresses-to-reveal address))]
                             (set-addresses-to-reveal new-addresses)))]
    [quo/account-permissions
     {:account             {:name                name
                            :address             address
                            :emoji               emoji
                            :customization-color customization-color}
      :token-details       (when-not share-all-addresses?
                             (balances->components-props balances images-by-symbol))
      :checked?            checked?
      :disabled?           share-all-addresses?
      :on-change           toggle-address
      :container-style     {:margin-bottom 8}
      :customization-color community-color}]))

(defn- page-top
  [{:keys [community-id identical-choices? can-edit-addresses?]}]
  (let [{:keys [name logo color]} (rf/sub [:communities/for-context-tag community-id])
        has-permissions? (rf/sub [:communities/has-permissions? community-id])
        confirm-discard-changes
        (rn/use-callback
         (fn []
           (if identical-choices?
             (rf/dispatch [:dismiss-modal :addresses-for-permissions])
             (rf/dispatch [:show-bottom-sheet
                           {:content (fn [] [confirm-discard-drawer
                                             community-id])}])))
         [identical-choices? community-id])

        open-permission-sheet
        (rn/use-callback (fn []
                           (rf/dispatch [:show-bottom-sheet
                                         {:content (fn [] [permissions-sheet/view
                                                           community-id])}]))
                         [community-id])]
    [:<>
     (when can-edit-addresses?
       [quo/page-nav
        {:type      :no-title
         :icon-name :i/arrow-left
         :on-press  confirm-discard-changes}])

     (if can-edit-addresses?
       [quo/page-top
        {:title                     (i18n/label :t/addresses-for-permissions)
         :title-accessibility-label :title-label
         :description               :context-tag
         :context-tag               {:type           :community
                                     :community-logo logo
                                     :community-name name}}]
       [quo/drawer-top
        (cond-> {:type                :context-tag
                 :title               (i18n/label :t/addresses-for-permissions)
                 :context-tag-type    :community
                 :community-name      name
                 :community-logo      logo
                 :customization-color color}
          has-permissions?
          (assoc :button-icon     :i/info
                 :button-type     :grey
                 :on-button-press open-permission-sheet))])]))

(defn view
  []
  (let [{id :community-id} (rf/sub [:get-screen-params])
        {:keys [color joined] outro-message :outroMessage} (rf/sub [:communities/community id])

        highest-role (rf/sub [:communities/highest-role-for-selection id])
        [unmodified-role _] (rn/use-state highest-role)

        can-edit-addresses? (rf/sub [:communities/can-edit-shared-addresses? id])
        pending? (boolean (rf/sub [:communities/my-pending-request-to-join id]))

        wallet-accounts (rf/sub [:wallet/accounts-without-watched-accounts])
        unmodified-addresses-to-reveal (rf/sub [:communities/addresses-to-reveal id])
        [addresses-to-reveal set-addresses-to-reveal] (rn/use-state unmodified-addresses-to-reveal)

        unmodified-flag-share-all-addresses (rf/sub [:communities/share-all-addresses? id])
        [flag-share-all-addresses
         set-flag-share-all-addresses] (rn/use-state unmodified-flag-share-all-addresses)

        identical-choices? (and (= unmodified-addresses-to-reveal addresses-to-reveal)
                                (= unmodified-flag-share-all-addresses flag-share-all-addresses))

        toggle-flag-share-all-addresses
        (fn []
          (let [new-value (not flag-share-all-addresses)]
            (set-flag-share-all-addresses new-value)
            (when new-value
              (set-addresses-to-reveal (set (map :address wallet-accounts))))))

        cancel-join-request
        (rn/use-callback
         (fn []
           (rf/dispatch [:show-bottom-sheet
                         {:content (fn [] [cancel-join-request-drawer id])}])))

        leave-community
        (rn/use-callback
         (fn []
           (rf/dispatch [:show-bottom-sheet
                         {:content (fn [] [leave-community-drawer id outro-message])}]))
         [outro-message])

        cancel-selection
        (fn []
          (rf/dispatch [:communities/check-permissions-to-join-community
                        id
                        unmodified-addresses-to-reveal
                        :based-on-client-selection])
          (rf/dispatch [:hide-bottom-sheet]))

        confirm-changes
        (fn []
          (if can-edit-addresses?
            (rf/dispatch
             [:password-authentication/show
              {:content (fn [] [password-authentication/view])}
              {:label    (i18n/label :t/enter-password)
               :on-press (fn [password]
                           (rf/dispatch
                            [:communities/edit-shared-addresses
                             {:community-id id
                              :password     password
                              :addresses    addresses-to-reveal
                              :on-success   (fn []
                                              (rf/dispatch [:dismiss-modal :addresses-for-permissions])
                                              (rf/dispatch [:hide-bottom-sheet]))}]))}])
            (do
              (rf/dispatch [:communities/set-share-all-addresses id flag-share-all-addresses])
              (rf/dispatch [:communities/set-addresses-to-reveal id addresses-to-reveal]))))]

    (rn/use-mount
     (fn []
       (when-not flag-share-all-addresses
         (rf/dispatch [:communities/get-permissioned-balances id]))))

    (rn/use-effect
     (fn []
       (rf/dispatch [:communities/check-permissions-to-join-during-selection id addresses-to-reveal]))
     [id addresses-to-reveal])

    [:<>
     [page-top
      {:community-id        id
       :identical-choices?  identical-choices?
       :can-edit-addresses? can-edit-addresses?}]

     [gesture/flat-list
      {:render-fn               account-item
       :render-data             [addresses-to-reveal
                                 set-addresses-to-reveal
                                 id
                                 color
                                 flag-share-all-addresses]
       :header                  [quo/category
                                 {:list-type       :settings
                                  :data            [{:title
                                                     (i18n/label
                                                      :t/share-all-current-and-future-addresses)
                                                     :action :selector
                                                     :action-props
                                                     {:on-change toggle-flag-share-all-addresses
                                                      :customization-color color
                                                      :checked? flag-share-all-addresses}}]
                                  :container-style {:padding-bottom 16 :padding-horizontal 0}}]
       :content-container-style {:padding-horizontal 20}
       :key-fn                  :address
       :data                    wallet-accounts}]

     [quo/bottom-actions
      (cond-> {:role             (role-keyword highest-role)
               :description      (if highest-role
                                   :top
                                   :top-error)
               :button-one-props {:customization-color color
                                  :on-press            confirm-changes
                                  :disabled?           (or (empty? addresses-to-reveal)
                                                           (not highest-role)
                                                           identical-choices?)}}
        can-edit-addresses?
        (->
          (assoc :actions              :one-action
                 :button-one-label     (cond
                                         (and pending? (not highest-role))
                                         (i18n/label :t/cancel-request)

                                         (and joined (not highest-role))
                                         (i18n/label :t/leave-community)

                                         :else
                                         (i18n/label :t/confirm-changes))
                 :description-top-text (cond
                                         (and pending? highest-role)
                                         (i18n/label :t/eligible-to-join-as)

                                         (and joined (= highest-role unmodified-role))
                                         (i18n/label :t/you-are-a)

                                         (and joined (not= highest-role unmodified-role))
                                         (i18n/label :t/you-will-be-a))
                 :error-message        (cond
                                         (and pending? (not highest-role))
                                         (i18n/label :t/community-join-requirements-not-met)

                                         (and joined (not highest-role))
                                         (i18n/label :t/membership-requirements-not-met)))
          (update :button-one-props
                  merge
                  (cond (and pending? (not highest-role))
                        {:type      :danger
                         :disabled? false
                         :on-press  cancel-join-request}

                        (and joined (not highest-role))
                        {:type      :danger
                         :disabled? false
                         :on-press  leave-community})))

        (not can-edit-addresses?)
        (assoc :actions          :two-actions
               :button-one-label (i18n/label :t/confirm-changes)
               :button-two-label (i18n/label :t/cancel)
               :button-two-props {:type     :grey
                                  :on-press cancel-selection}
               :error-message    (cond
                                   (empty? addresses-to-reveal)
                                   (i18n/label :t/no-addresses-selected)

                                   (not highest-role)
                                   (i18n/label :t/addresses-dont-contain-tokens-needed))))]]))
