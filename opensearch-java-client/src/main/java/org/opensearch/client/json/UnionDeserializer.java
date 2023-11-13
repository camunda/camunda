/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.client.json;

import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.JsonObject;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnionDeserializer<Union, Kind, Member> implements JsonpDeserializer<Union> {

    public static class AmbiguousUnionException extends RuntimeException {
        public AmbiguousUnionException(String message) {
            super(message);
        }
    }

    private abstract static class EventHandler<Union, Kind, Member> {
        abstract Union deserialize(JsonParser parser, JsonpMapper mapper, Event event, BiFunction<Kind, Member, Union> buildFn);
        abstract EnumSet<Event> nativeEvents();
    }

    private static class SingleMemberHandler<Union, Kind, Member> extends EventHandler<Union, Kind, Member> {
        private final JsonpDeserializer<? extends Member> deserializer;
        private final Kind tag;
        // ObjectDeserializers provide the list of fields they know about
        private final Set<String> fields;

        SingleMemberHandler(Kind tag, JsonpDeserializer<? extends Member> deserializer) {
            this(tag, deserializer, null);
        }

        SingleMemberHandler(Kind tag, JsonpDeserializer<? extends Member> deserializer, Set<String> fields) {
            this.deserializer = deserializer;
            this.tag = tag;
            this.fields = fields;
        }

        @Override
        EnumSet<Event> nativeEvents() {
            return deserializer.nativeEvents();
        }

        @Override
        Union deserialize(JsonParser parser, JsonpMapper mapper, Event event, BiFunction<Kind, Member, Union> buildFn) {
            return buildFn.apply(tag, deserializer.deserialize(parser, mapper, event));
        }
    }

    /**
     * An event handler for value events (string, number, etc) that can try multiple handlers, which are ordered
     * from most specific (e.g. enum) to least specific (e.g. string)
     */
    private static class MultiMemberHandler<Union, Kind, Member> extends EventHandler<Union, Kind, Member> {
        private List<SingleMemberHandler<Union, Kind, Member>> handlers;

        @Override
        EnumSet<Event> nativeEvents() {
            EnumSet<Event> result = EnumSet.noneOf(Event.class);
            for (SingleMemberHandler<Union, Kind, Member> smh: handlers) {
                result.addAll(smh.deserializer.nativeEvents());
            }
            return result;
        }

        @Override
        Union deserialize(JsonParser parser, JsonpMapper mapper, Event event, BiFunction<Kind, Member, Union> buildFn) {
            RuntimeException exception = null;
            for (EventHandler<Union, Kind, Member> d: handlers) {
                try {
                    return d.deserialize(parser, mapper, event, buildFn);
                } catch(RuntimeException ex) {
                    exception = ex;
                }
            }
            throw new JsonParsingException("Couldn't find a suitable union member deserializer", exception, parser.getLocation());
        }
    }

    public static class Builder<Union, Kind, Member> implements ObjectBuilder<JsonpDeserializer<Union>> {

        private final BiFunction<Kind, Member, Union> buildFn;

        private final List<UnionDeserializer.SingleMemberHandler<Union, Kind, Member>> objectMembers = new ArrayList<>();
        private final Map<Event, EventHandler<Union, Kind, Member>> otherMembers = new HashMap<>();
        private final boolean allowAmbiguousPrimitive;

        public Builder(BiFunction<Kind, Member, Union> buildFn, boolean allowAmbiguities) {
            // If we allow ambiguities, multiple handlers for a given JSON value event will be allowed
            this.allowAmbiguousPrimitive = allowAmbiguities;
            this.buildFn = buildFn;
        }

        private void addAmbiguousDeserializer(Event e, Kind tag, JsonpDeserializer<? extends Member> deserializer) {
            EventHandler<Union, Kind, Member> m = otherMembers.get(e);
            MultiMemberHandler<Union, Kind, Member> mmh;
            if (m instanceof MultiMemberHandler<?, ?, ?>) {
                mmh = (MultiMemberHandler<Union, Kind, Member>) m;
            } else {
                mmh = new MultiMemberHandler<>();
                mmh.handlers = new ArrayList<>(2);
                mmh.handlers.add((SingleMemberHandler<Union, Kind, Member>) m);
                otherMembers.put(e, mmh);
            }
            mmh.handlers.add(new SingleMemberHandler<>(tag, deserializer));
            // Sort handlers by number of accepted events, which gives their specificity
            mmh.handlers.sort(Comparator.comparingInt(a -> a.deserializer.acceptedEvents().size()));
        }

        private void addMember(Event e, Kind tag, UnionDeserializer.SingleMemberHandler<Union, Kind, Member> member) {
            if (otherMembers.containsKey(e)) {
                if (!allowAmbiguousPrimitive || e == Event.START_OBJECT || e == Event.START_ARRAY) {
                    throw new AmbiguousUnionException("Union member '" + tag + "' conflicts with other members");
                } else {
                    // Allow ambiguities on value event
                    addAmbiguousDeserializer(e, tag, member.deserializer);
                }
            } else {
                // Note: we accept START_OBJECT here. It can be a user-provided type, and will be used
                // as a fallback if no element of objectMembers matches.
                otherMembers.put(e, member);
            }
        }

        public Builder<Union, Kind, Member> addMember(Kind tag, JsonpDeserializer<? extends Member> deserializer) {

            JsonpDeserializer<?> unwrapped = DelegatingDeserializer.unwrap(deserializer);
            if (unwrapped instanceof ObjectDeserializer) {
                ObjectDeserializer<?> od = (ObjectDeserializer<?>) unwrapped;
                UnionDeserializer.SingleMemberHandler<Union, Kind, Member> member =
                        new SingleMemberHandler<>(tag, deserializer, new HashSet<>(od.fieldNames()));
                objectMembers.add(member);
                if (od.shortcutProperty() != null) {
                    // also add it as a string
                    addMember(Event.VALUE_STRING, tag, member);
                }
            } else {
                UnionDeserializer.SingleMemberHandler<Union, Kind, Member> member = new SingleMemberHandler<>(tag, deserializer);
                for (Event e: deserializer.nativeEvents()) {
                    addMember(e, tag, member);
                }
            }

            return this;
        }

        @Override
        public JsonpDeserializer<Union> build() {
            Map<String, Long> fieldFrequencies = objectMembers.stream().flatMap(m -> m.fields.stream())
                    .collect( Collectors.groupingBy(Function.identity(), Collectors.counting()));
            Set<String> duplicateFields = fieldFrequencies.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            for (UnionDeserializer.SingleMemberHandler<Union, Kind, Member> member: objectMembers) {
                member.fields.removeAll(duplicateFields);
            }

            // Check that no object member had all its fields removed
            for (UnionDeserializer.SingleMemberHandler<Union, Kind, Member> member: objectMembers) {
                if (member.fields.isEmpty()) {
                    throw new AmbiguousUnionException("All properties of '" + member.tag + "' also exist in other object members");
                }
            }

            if (objectMembers.size() == 1 && !otherMembers.containsKey(Event.START_OBJECT)) {
                // A single deserializer handles objects: promote it to otherMembers as we don't need property-based disambiguation
                otherMembers.put(Event.START_OBJECT, objectMembers.remove(0));
            }

//            if (objectMembers.size() > 1) {
//                System.out.println("multiple objects in " + buildFn);
//            }

            return new UnionDeserializer<>(objectMembers, otherMembers, buildFn);
        }
    }

    private final BiFunction<Kind, Member, Union> buildFn;
    private final EnumSet<Event> nativeEvents;
    private final Map<String, EventHandler<Union, Kind, Member>> objectMembers;
    private final Map<Event, EventHandler<Union, Kind, Member>> nonObjectMembers;
    private final EventHandler<Union, Kind, Member> fallbackObjectMember;

    public UnionDeserializer(
        List<SingleMemberHandler<Union, Kind, Member>> objectMembers,
        Map<Event, EventHandler<Union, Kind, Member>> nonObjectMembers,
        BiFunction<Kind, Member, Union> buildFn
    ) {
        this.buildFn = buildFn;

        // Build a map of (field name -> member) for all fields to speed up lookup
        if (objectMembers.isEmpty()) {
            this.objectMembers = Collections.emptyMap();
        } else {
            this.objectMembers = new HashMap<>();
            for (SingleMemberHandler<Union, Kind, Member> member: objectMembers) {
                for (String field: member.fields) {
                    this.objectMembers.put(field, member);
                }
            }
        }

        this.nonObjectMembers = nonObjectMembers;

        this.nativeEvents = EnumSet.noneOf(Event.class);
        for (EventHandler<Union, Kind, Member> member: nonObjectMembers.values()) {
            this.nativeEvents.addAll(member.nativeEvents());
        }

        if (objectMembers.isEmpty()) {
            fallbackObjectMember = null;
        } else {
            fallbackObjectMember = this.nonObjectMembers.remove(Event.START_OBJECT);
            this.nativeEvents.add(Event.START_OBJECT);
        }
    }

    @Override
    public EnumSet<Event> nativeEvents() {
        return nativeEvents;
    }

    @Override
    public EnumSet<Event> acceptedEvents() {
        // In a union we want the real thing
        return nativeEvents;
    }

    @Override
    public Union deserialize(JsonParser parser, JsonpMapper mapper) {
        Event event = parser.next();
        JsonpUtils.ensureAccepts(this, parser, event);
        return deserialize(parser, mapper, event);
    }

    @Override
    public Union deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
        EventHandler<Union, Kind, Member> member = nonObjectMembers.get(event);
        JsonLocation location = parser.getLocation();

        if (member == null && event == Event.START_OBJECT && !objectMembers.isEmpty()) {
            if (parser instanceof LookAheadJsonParser) {
                Map.Entry<EventHandler<Union, Kind, Member>, JsonParser> memberAndParser =
                        ((LookAheadJsonParser) parser).findVariant(objectMembers);

                member = memberAndParser.getKey();
                // Parse the buffered parser
                parser = memberAndParser.getValue();

            } else {
                // Parse as an object to find matching field names
                JsonObject object = parser.getObject();

                for (String field: object.keySet()) {
                    member = objectMembers.get(field);
                    if (member != null) {
                        break;
                    }
                }

                // Traverse the object we have inspected
                parser = JsonpUtils.objectParser(object, mapper);
            }

            if (member == null) {
                member = fallbackObjectMember;
            }

            if (member != null) {
                event = parser.next();
            }
        }

        if (member == null) {
            throw new JsonParsingException("Cannot determine what union member to deserialize", location);
        }

        return member.deserialize(parser, mapper, event, buildFn);
    }
}
