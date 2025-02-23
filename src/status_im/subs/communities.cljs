(ns status-im.subs.communities
  (:require
    [clojure.string :as string]
    [legacy.status-im.ui.screens.profile.visibility-status.utils :as visibility-status-utils]
    [re-frame.core :as re-frame]
    [status-im.constants :as constants]
    [status-im.contexts.wallet.common.utils :as wallet.utils]
    [utils.i18n :as i18n]))

(re-frame/reg-sub
 :communities/fetching-community
 :<- [:communities/fetching-communities]
 (fn [info [_ id]]
   (get info id)))

(re-frame/reg-sub
 :communities/section-list
 :<- [:communities]
 (fn [communities]
   (->> (vals communities)
        (group-by (comp (fnil string/upper-case "") first :name))
        (sort-by (fn [[title]] title))
        (map (fn [[title data]]
               {:title title
                :data  data})))))

(re-frame/reg-sub
 :communities/community
 :<- [:communities]
 (fn [communities [_ id]]
   (get communities id)))

(re-frame/reg-sub
 :communities/community-chats
 :<- [:communities]
 (fn [communities [_ id]]
   (get-in communities [id :chats])))

(re-frame/reg-sub
 :communities/community-color
 :<- [:communities]
 (fn [communities [_ id]]
   (get-in communities [id :color])))

(re-frame/reg-sub
 :communities/community-members
 :<- [:communities]
 (fn [communities [_ id]]
   (get-in communities [id :members])))

(re-frame/reg-sub
 :communities/current-community-members
 :<- [:chats/current-chat]
 :<- [:communities]
 (fn [[{:keys [community-id]} communities]]
   (get-in communities [community-id :members])))

(re-frame/reg-sub
 :communities/sorted-community-members
 (fn [[_ community-id]]
   (let [profile (re-frame/subscribe [:profile/profile])
         members (re-frame/subscribe [:communities/community-members community-id])]
     [profile members]))
 (fn [[profile members] _]
   (let [names (reduce (fn [acc contact-identity]
                         (assoc acc
                                contact-identity
                                (when (= (:public-key profile) contact-identity)
                                  (:primary-name profile)
                                  contact-identity)))
                       {}
                       (keys members))]
     (->> members
          (sort-by #(get names (get % 0)))
          (sort-by #(visibility-status-utils/visibility-status-order (get % 0)))))))

(re-frame/reg-sub
 :communities/featured-contract-communities
 :<- [:contract-communities]
 (fn [contract-communities]
   (sort-by :name (vals (:featured contract-communities)))))

(re-frame/reg-sub
 :communities/other-contract-communities
 :<- [:contract-communities]
 (fn [contract-communities]
   (sort-by :name (vals (:other contract-communities)))))

(re-frame/reg-sub
 :communities/community-ids
 :<- [:communities]
 (fn [communities]
   (map :id (vals communities))))

(def memo-communities-stack-items (atom nil))

(defn- merge-opened-communities
  [{:keys [joined pending] :as assorted-communities}]
  (update assorted-communities :opened concat joined pending))

(defn- group-communities-by-status
  [requests
   {:keys [id]
    :as   community}]
  (cond
    (:joined community)         :joined
    (boolean (get requests id)) :pending
    :else                       :opened))

(re-frame/reg-sub
 :communities/grouped-by-status
 :<- [:view-id]
 :<- [:communities]
 :<- [:communities/my-pending-requests-to-join]
 ;; Return communities splitted by level of user participation. Some communities user already
 ;; joined, to some of them join request sent and others were opened one day and their data remained
 ;; in app-db. Result map has form: {:joined [id1, id2] :pending [id3, id5] :opened [id4]}"
 (fn [[view-id communities requests]]
   (if (or (empty? @memo-communities-stack-items) (= view-id :communities-stack))
     (let [grouped-communities (->> communities
                                    vals
                                    (group-by #(group-communities-by-status requests %))
                                    merge-opened-communities
                                    (map (fn [[k v]]
                                           {k (sort-by (fn [{:keys [requested-to-join-at last-opened-at
                                                                    joined-at]}]
                                                         (condp = k
                                                           :joined  joined-at
                                                           :pending requested-to-join-at
                                                           :opened  last-opened-at
                                                           last-opened-at))
                                                       #(compare %2 %1)
                                                       v)}))
                                    (into {}))]
       (reset! memo-communities-stack-items grouped-communities)
       grouped-communities)
     @memo-communities-stack-items)))

(defn community->home-item
  [community counts]
  {:name                  (:name community)
   :muted?                (:muted community)
   :unread-messages?      (pos? (:unviewed-messages-count counts))
   :unread-mentions-count (:unviewed-mentions-count counts)
   :community-icon        (:images community)})

(re-frame/reg-sub
 :communities/home-item
 (fn [[_ community-id]]
   [(re-frame/subscribe [:communities])
    (re-frame/subscribe [:communities/unviewed-counts community-id])])
 (fn [[communities counts] [_ community-identity]]
   (community->home-item
    (get communities community-identity)
    counts)))

(re-frame/reg-sub
 :communities/my-pending-request-to-join
 :<- [:communities/my-pending-requests-to-join]
 (fn [requests [_ community-id]]
   (:id (get requests community-id))))

(re-frame/reg-sub
 :communities/edited-community
 :<- [:communities]
 :<- [:communities/community-id-input]
 (fn [[communities community-id]]
   (get communities community-id)))

(re-frame/reg-sub
 :communities/current-community
 :<- [:communities]
 :<- [:chats/current-raw-chat]
 (fn [[communities {:keys [community-id]}]]
   (get communities community-id)))

(re-frame/reg-sub
 :communities/unviewed-count
 (fn [[_ community-id]]
   [(re-frame/subscribe [:chats/by-community-id community-id])])
 (fn [[chats]]
   (reduce (fn [acc {:keys [unviewed-messages-count]}]
             (+ acc (or unviewed-messages-count 0)))
           0
           chats)))

(defn calculate-unviewed-counts
  [chats]
  (reduce (fn [acc {:keys [unviewed-mentions-count unviewed-messages-count muted]}]
            (if-not muted
              (-> acc
                  (update :unviewed-messages-count + unviewed-messages-count)
                  (update :unviewed-mentions-count + unviewed-mentions-count))
              acc))
          {:unviewed-messages-count 0
           :unviewed-mentions-count 0}
          chats))

(re-frame/reg-sub
 :communities/unviewed-counts
 (fn [[_ community-id]]
   [(re-frame/subscribe [:chats/by-community-id community-id])])
 (fn [[chats]]
   (calculate-unviewed-counts chats)))

(re-frame/reg-sub
 :communities/requests-to-join-for-community
 :<- [:communities/requests-to-join]
 (fn [requests [_ community-id]]
   (->>
     (get requests community-id {})
     vals
     (filter (fn [{:keys [state]}]
               (= state constants/request-to-join-pending-state))))))

(re-frame/reg-sub
 :community/categories
 (fn [[_ community-id]]
   [(re-frame/subscribe [:communities/community community-id])])
 (fn [[{:keys [categories]}] _]
   categories))

(re-frame/reg-sub
 :communities/sorted-categories
 :<- [:communities]
 (fn [communities [_ id]]
   (->> (get-in communities [id :categories])
        (map #(assoc (get % 1) :community-id id))
        (sort-by :position)
        (into []))))

(defn- reduce-over-categories
  [community-id
   categories
   collapsed-categories
   full-chats-data]
  (fn [acc
       [_
        {:keys [name
                categoryID
                position
                id
                emoji
                can-view?
                can-post?
                token-gated?]}]]
    (let [category-id       (if (seq categoryID) categoryID constants/empty-category-id)
          {:keys [unviewed-messages-count
                  unviewed-mentions-count
                  muted]}   (get full-chats-data
                                 (str community-id id))
          acc-with-category (if (get acc category-id)
                              acc
                              (assoc acc
                                     category-id
                                     (assoc
                                      (or (get categories category-id)
                                          {:name (i18n/label :t/none)})
                                      :collapsed? (get collapsed-categories
                                                       category-id)
                                      :chats      [])))
          categorized-chat  {:name             name
                             :emoji            emoji
                             :muted?           muted
                             :unread-messages? (pos? unviewed-messages-count)
                             :position         position
                             :mentions-count   (or unviewed-mentions-count 0)
                             :can-post?        can-post?
                             ;; NOTE: this is a troolean nil->no permissions, true->no access, false
                             ;; -> has access
                             :locked?          (when token-gated?
                                                 (and (not can-view?)
                                                      (not can-post?)))
                             :id               id}]
      (update-in acc-with-category [category-id :chats] conj categorized-chat))))

(re-frame/reg-sub
 :communities/categorized-channels
 (fn [[_ community-id]]
   [(re-frame/subscribe [:communities/community community-id])
    (re-frame/subscribe [:chats/chats])
    (re-frame/subscribe [:communities/collapsed-categories-for-community community-id])])
 (fn [[{:keys [categories chats]} full-chats-data collapsed-categories]
      [_ community-id]]
   (let [reduce-fn (reduce-over-categories
                    community-id
                    categories
                    collapsed-categories
                    full-chats-data)
         categories-and-chats
         (->> chats
              (reduce reduce-fn {})
              (sort-by (comp :position second))
              (map (fn [[k v]]
                     [k (update v :chats #(sort-by :position %))])))]
     categories-and-chats)))

(re-frame/reg-sub
 :communities/collapsed-categories-for-community
 :<- [:communities/collapsed-categories]
 (fn [collapsed-categories [_ community-id]]
   (get collapsed-categories community-id)))


(defn token-requirement->token
  [checking-permissions?
   token-images
   {:keys [satisfied criteria]}]
  (let [sym    (:symbol criteria)
        amount (:amount criteria)]
    {:symbol      sym
     :sufficient? satisfied
     :loading?    checking-permissions?
     :amount      (wallet.utils/remove-trailing-zeroes amount)
     :img-src     (get token-images sym)}))

(re-frame/reg-sub
 :communities/checking-permissions-by-id
 :<- [:communities/permissions-check]
 (fn [permissions [_ id]]
   (get permissions id)))

(re-frame/reg-sub
 :communities/checking-permissions-all-by-id
 :<- [:communities/permissions-check-all]
 (fn [permissions [_ id]]
   (get permissions id)))

(re-frame/reg-sub
 :community/token-gated-overview
 (fn [[_ community-id]]
   [(re-frame/subscribe [:communities/community community-id])
    (re-frame/subscribe [:communities/checking-permissions-by-id community-id])])
 (fn [[{:keys [token-images]}
       {:keys [checking? check]}] _]
   (let [highest-role            (:highestRole check)
         networks-not-supported? (:networksNotSupported check)
         lowest-role             (last (:roles check))
         highest-permission-role (:type highest-role)
         can-request-access?     (and (boolean highest-permission-role) (not networks-not-supported?))]
     {:can-request-access?     can-request-access?
      :checking?               checking?
      :highest-permission-role highest-permission-role
      :networks-not-supported? networks-not-supported?
      :no-member-permission?   (and highest-permission-role
                                    (not (-> check :highestRole :criteria)))
      :tokens                  (map (fn [{:keys [tokenRequirement]}]
                                      (map
                                       (partial token-requirement->token
                                                checking?
                                                token-images)
                                       tokenRequirement))
                                    (or (:criteria highest-role)
                                        (:criteria lowest-role)))})))

(re-frame/reg-sub
 :community/images
 :<- [:communities]
 (fn [communities [_ id]]
   (get-in communities [id :images])))

(re-frame/reg-sub
 :communities/rules
 :<- [:communities]
 (fn [communities [_ community-id]]
   (get-in communities [community-id :intro-message])))

(re-frame/reg-sub
 :communities/token-images-by-symbol
 (fn [[_ community-id]]
   [(re-frame/subscribe [:communities/community community-id])])
 (fn [[{:keys [tokens-metadata]}] _]
   (->> tokens-metadata
        (map (fn [{sym :symbol image :image}]
               {sym image}))
        (into {}))))

(re-frame/reg-sub
 :community/token-permissions
 (fn [[_ community-id]]
   [(re-frame/subscribe [:communities/community community-id])
    (re-frame/subscribe [:communities/checking-permissions-all-by-id community-id])])
 (fn [[{:keys [token-images]}
       {:keys [checking? check]}] _]
   (let [roles                      (:roles check)
         member-and-satisifed-roles (filter #(or (= (:type %)
                                                    constants/community-token-permission-become-member)
                                                 (:satisfied %))
                                            roles)]
     (mapv (fn [role]
             {:role       (:type role)
              :satisfied? (:satisfied role)
              :tokens     (map (fn [{:keys [tokenRequirement]}]
                                 (map
                                  (partial token-requirement->token
                                           checking?
                                           token-images)
                                  tokenRequirement))
                               (:criteria role))})
           member-and-satisifed-roles))))

(re-frame/reg-sub
 :communities/has-permissions?
 (fn [[_ community-id]]
   [(re-frame/subscribe [:community/token-permissions community-id])])
 (fn [[permissions] _]
   (let [all-tokens (apply concat (map :tokens permissions))]
     (boolean (some seq all-tokens)))))
