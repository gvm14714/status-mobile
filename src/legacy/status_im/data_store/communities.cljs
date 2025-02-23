(ns legacy.status-im.data-store.communities
  (:require
    [clojure.set :as set]
    [clojure.walk :as walk]
    [status-im.constants :as constants]))

(defn <-revealed-accounts-rpc
  [accounts]
  (mapv
   #(set/rename-keys % {:isAirdropAddress :airdrop-address?})
   (js->clj accounts :keywordize-keys true)))

(defn <-request-to-join-community-rpc
  [r]
  (set/rename-keys r
                   {:communityId :community-id
                    :publicKey   :public-key
                    :chatId      :chat-id}))

(defn <-requests-to-join-community-rpc
  [requests key-fn]
  (reduce #(assoc %1 (key-fn %2) (<-request-to-join-community-rpc %2)) {} requests))

(defn <-chats-rpc
  [chats]
  (reduce-kv (fn [acc k v]
               (assoc acc
                      (name k)
                      (-> v
                          (assoc :token-gated? (:tokenGated v)
                                 :can-post?    (:canPost v)
                                 :can-view?    (:canView v))
                          (dissoc :canPost :tokenGated :canView)
                          (update :members walk/stringify-keys))))
             {}
             chats))

(defn <-categories-rpc
  [categ]
  (reduce-kv #(assoc %1 (name %2) %3) {} categ))

(defn role-permission?
  [token-permission]
  (contains? constants/community-role-permissions (:type token-permission)))

(defn membership-permission?
  [token-permission]
  (= (:type token-permission) constants/community-token-permission-become-member))

(defn <-rpc
  [c]
  (-> c
      (set/rename-keys
       {:canRequestAccess            :can-request-access?
        :canManageUsers              :can-manage-users?
        :canDeleteMessageForEveryone :can-delete-message-for-everyone?
        ;; This flag is misleading based on its name alone
        ;; because it should not be used to decide if the user
        ;; is *allowed* to join. Allowance is based on token
        ;; permissions. Still, the flag can be used to know
        ;; whether or not the user will have to wait until an
        ;; admin approves a join request.
        :canJoin                     :can-join?
        :requestedToJoinAt           :requested-to-join-at
        :isMember                    :is-member?
        :adminSettings               :admin-settings
        :tokenPermissions            :token-permissions
        :communityTokensMetadata     :tokens-metadata
        :introMessage                :intro-message
        :muteTill                    :muted-till
        :lastOpenedAt                :last-opened-at
        :joinedAt                    :joined-at})
      (update :admin-settings
              set/rename-keys
              {:pinMessageAllMembersEnabled :pin-message-all-members-enabled?})
      (update :members walk/stringify-keys)
      (update :chats <-chats-rpc)
      (update :token-permissions seq)
      (update :categories <-categories-rpc)
      (assoc :role-permissions?
             (->> c
                  :tokenPermissions
                  vals
                  (some role-permission?)))
      (assoc :membership-permissions?
             (->> c
                  :tokenPermissions
                  vals
                  (some membership-permission?)))
      (assoc :token-images
             (reduce (fn [acc {sym :symbol image :image}]
                       (assoc acc sym image))
                     {}
                     (:communityTokensMetadata c)))))
