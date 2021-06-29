/*
 * Copyright (C) 2016-2021 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.gmt2001.eventsub;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * EventSub Subscription Data
 *
 * @author gmt2001
 */
public class EventSubSubscription {

    private final String id;
    private final subscription_status status;
    private final String type;
    private final String version;
    private final int cost;
    private final Map<String, String> condition;
    private final Date created_at;
    private final EventSubTransport transport;

    /**
     * The status of an EventSub subscription
     */
    public enum subscription_status {
        /**
         * Designates that the subscription is in an operable state and is valid.
         */
        ENABLED,
        /**
         * Webhook is pending verification of the callback specified in the subscription creation request.
         */
        WEBHOOK_CALLBACK_VERIFICATION_PENDING,
        /**
         * Webhook failed verification of the callback specified in the subscription creation request.
         */
        WEBHOOK_CALLBACK_VERIFICATION_FAILED,
        /**
         * Notification delivery failure rate was too high.
         */
        NOTIFICATION_FAILURES_EXCEEDED,
        /**
         * Authorization for user(s) in the condition was revoked.
         */
        AUTHORIZATION_REVOKED,
        /**
         * A user in the condition of the subscription was removed.
         */
        USER_REMOVED,
        /**
         * Subscription proposal that has not been submitted to the EventSub endpoint for creation yet.
         */
        NOT_CREATED_YET
    }

    EventSubSubscription(String id, String status, String type, String version, int cost, Map<String, String> condition, String created_at, EventSubTransport transport) {
        this.id = id;
        this.status = subscription_status.valueOf(status.toUpperCase());
        this.type = type;
        this.version = version;
        this.cost = cost;
        this.condition = new HashMap<>(condition);
        this.created_at = this.parseDate(created_at);
        this.transport = transport;
    }

    EventSubSubscription(String id, subscription_status status, String type, String version, int cost, Map<String, String> condition, String created_at, EventSubTransport transport) {
        this.id = id;
        this.status = status;
        this.type = type;
        this.version = version;
        this.cost = cost;
        this.condition = new HashMap<>(condition);
        this.created_at = this.parseDate(created_at);
        this.transport = transport;
    }

    EventSubSubscription(String id, String status, String type, String version, int cost, Map<String, String> condition, Date created_at, EventSubTransport transport) {
        this.id = id;
        this.status = subscription_status.valueOf(status.toUpperCase());
        this.type = type;
        this.version = version;
        this.cost = cost;
        this.condition = new HashMap<>(condition);
        this.created_at = created_at;
        this.transport = transport;
    }

    EventSubSubscription(String id, subscription_status status, String type, String version, int cost, Map<String, String> condition, Date created_at, EventSubTransport transport) {
        this.id = id;
        this.status = status;
        this.type = type;
        this.version = version;
        this.cost = cost;
        this.condition = new HashMap<>(condition);
        this.created_at = created_at;
        this.transport = transport;
    }

    EventSubSubscription(String type, String version, Map<String, String> condition, EventSubTransport transport) {
        this.id = "";
        this.status = subscription_status.NOT_CREATED_YET;
        this.type = type;
        this.version = version;
        this.cost = -1;
        this.condition = new HashMap<>(condition);
        this.created_at = new Date();
        this.transport = transport;
    }

    private Date parseDate(String date) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            return fmt.parse(date);
        } catch (ParseException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }

        return new Date();
    }

    /**
     * The subscription's unique id.
     *
     * @return
     */
    public String getId() {
        return this.id;
    }

    /**
     * The status of the subscription.
     *
     * @return
     */
    public subscription_status getStatus() {
        return this.status;
    }

    /**
     * The subscription's type.
     *
     * @return
     */
    public String getType() {
        return this.type;
    }

    /**
     * The version of the subscription.
     *
     * @return
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * How much the subscription counts against your limit. See https://dev.twitch.tv/docs/eventsub#subscription-limits for more information.
     *
     * @return
     */
    public int getCost() {
        return this.cost;
    }

    /**
     * Subscription-specific parameters.
     *
     * @return
     */
    public Map<String, String> getCondition() {
        return new HashMap<>(this.condition);
    }

    /**
     * The time the subscription was created.
     *
     * @return
     */
    public Date getCreatedAt() {
        return this.created_at;
    }

    /**
     * Transport-specific parameters.
     *
     * @return
     */
    public EventSubTransport getTransport() {
        return this.transport;
    }
}
