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

package org.opensearch.client.json.jackson;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.JsonParserSequence;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonLocation;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;
import org.opensearch.client.json.LookAheadJsonParser;
import org.opensearch.client.json.UnexpectedJsonEventException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * A JSONP parser implementation on top of Jackson.
 * <p>
 * <b>Warning:</b> this implementation isn't fully compliant with the JSONP specification: calling {@link #hasNext()}
 * moves forward the underlying Jackson parser as Jackson doesn't provide an equivalent method. This means no value
 * getter method (e.g. {@link #getInt()} or {@link #getString()} should be called until the next call to {@link #next()}.
 * Such calls will throw an {@code IllegalStateException}.
 */
public class JacksonJsonpParser implements LookAheadJsonParser {

    private final com.fasterxml.jackson.core.JsonParser parser;

    private boolean hasNextWasCalled = false;

    private static final EnumMap<JsonToken, Event> tokenToEvent;

    static {
        tokenToEvent = new EnumMap<>(JsonToken.class);
        tokenToEvent.put(JsonToken.END_ARRAY, Event.END_ARRAY);
        tokenToEvent.put(JsonToken.END_OBJECT, Event.END_OBJECT);
        tokenToEvent.put(JsonToken.FIELD_NAME, Event.KEY_NAME);
        tokenToEvent.put(JsonToken.START_ARRAY, Event.START_ARRAY);
        tokenToEvent.put(JsonToken.START_OBJECT, Event.START_OBJECT);
        tokenToEvent.put(JsonToken.VALUE_FALSE, Event.VALUE_FALSE);
        tokenToEvent.put(JsonToken.VALUE_NULL, Event.VALUE_NULL);
        tokenToEvent.put(JsonToken.VALUE_NUMBER_FLOAT, Event.VALUE_NUMBER);
        tokenToEvent.put(JsonToken.VALUE_NUMBER_INT, Event.VALUE_NUMBER);
        tokenToEvent.put(JsonToken.VALUE_STRING, Event.VALUE_STRING);
        tokenToEvent.put(JsonToken.VALUE_TRUE, Event.VALUE_TRUE);

        // No equivalent for
        // - VALUE_EMBEDDED_OBJECT
        // - NOT_AVAILABLE
    }

    public JacksonJsonpParser(com.fasterxml.jackson.core.JsonParser parser) {
        this.parser = parser;
    }

    /**
     * Returns the underlying Jackson parser.
     */
    public com.fasterxml.jackson.core.JsonParser jacksonParser() {
        return this.parser;
    }

    private JsonParsingException convertException(IOException ioe) {
        return new JsonParsingException("Jackson exception: " + ioe.getMessage(), ioe, getLocation());
    }

    private JsonToken fetchNextToken() {
        try {
            return parser.nextToken();
        } catch(IOException e) {
            throw convertException(e);
        }
    }

    private void ensureTokenIsCurrent() {
        if (hasNextWasCalled) {
            throw new IllegalStateException("Cannot get event data as parser as already been moved to the next event");
        }
    }

    @Override
    public boolean hasNext() {
        if (hasNextWasCalled) {
            return parser.currentToken() != null;
        } else {
            hasNextWasCalled = true;
            return fetchNextToken() != null;
        }
    }

    @Override
    public Event next() {
        JsonToken token;
        if (hasNextWasCalled) {
            token = parser.getCurrentToken();
            hasNextWasCalled = false;
        } else {
            token = fetchNextToken();
        }

        if (token == null) {
            throw new NoSuchElementException();
        }

        Event result = tokenToEvent.get(token);
        if (result == null) {
            throw new JsonParsingException("Unsupported Jackson event type '"+ token + "'", getLocation());
        }

        return result;
    }

    @Override
    public String getString() {
        ensureTokenIsCurrent();
        try {
            return parser.getValueAsString();
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public boolean isIntegralNumber() {
        ensureTokenIsCurrent();
        return parser.isExpectedNumberIntToken();
    }

    @Override
    public int getInt() {
        ensureTokenIsCurrent();
        try {
            return parser.getIntValue();
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public long getLong() {
        ensureTokenIsCurrent();
        try {
            return parser.getLongValue();
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public BigDecimal getBigDecimal() {
        ensureTokenIsCurrent();
        try {
            return parser.getDecimalValue();
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public JsonLocation getLocation() {
        return new JacksonJsonpLocation(parser.getCurrentLocation());
    }

    @Override
    public void close() {
        try {
            parser.close();
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    private JsonValueParser valueParser;

    @Override
    public JsonObject getObject() {
        ensureTokenIsCurrent();
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IllegalStateException("Unexpected event '" + parser.currentToken() +
                "' at " + parser.getTokenLocation());
        }
        if (valueParser == null) {
            valueParser = new JsonValueParser();
        }
        try {
            return valueParser.parseObject(parser);
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public JsonArray getArray() {
        ensureTokenIsCurrent();
        if (valueParser == null) {
            valueParser = new JsonValueParser();
        }
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new IllegalStateException("Unexpected event '" + parser.currentToken() +
                "' at " + parser.getTokenLocation());
        }
        try {
            return valueParser.parseArray(parser);
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public JsonValue getValue() {
        ensureTokenIsCurrent();
        if (valueParser == null) {
            valueParser = new JsonValueParser();
        }
        try {
            return valueParser.parseValue(parser);
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public void skipObject() {
        ensureTokenIsCurrent();
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return;
        }

        try {
            int depth = 1;
            JsonToken token;
            do {
                token = parser.nextToken();
                switch (token) {
                    case START_OBJECT:
                        depth++;
                        break;
                    case END_OBJECT:
                        depth--;
                        break;
                }
            } while(!(token == JsonToken.END_OBJECT && depth == 0));
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public void skipArray() {
        ensureTokenIsCurrent();
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            return;
        }

        try {
            int depth = 1;
            JsonToken token;
            do {
                token = parser.nextToken();
                switch (token) {
                    case START_ARRAY:
                        depth++;
                        break;
                    case END_ARRAY:
                        depth--;
                        break;
                }
            } while(!(token == JsonToken.END_ARRAY && depth == 0));
        } catch (IOException e) {
            throw convertException(e);
        }
    }

    @Override
    public Stream<Map.Entry<String, JsonValue>> getObjectStream() {
        return getObject().entrySet().stream();
    }

    @Override
    public Stream<JsonValue> getArrayStream() {
        return getArray().stream();
    }

    /**
     * Not implemented.
     */
    @Override
    public Stream<JsonValue> getValueStream() {
        return LookAheadJsonParser.super.getValueStream();
    }

    //----- Look ahead methods

    public Map.Entry<String, JsonParser> lookAheadFieldValue(String name, String defaultValue) {

        TokenBuffer tb = new TokenBuffer(parser, null);

        try {
            // The resulting parser must contain the full object, including START_EVENT
            tb.copyCurrentEvent(parser);
            while (parser.nextToken() != JsonToken.END_OBJECT) {

                expectEvent(JsonToken.FIELD_NAME);
                // Do not copy current event here, each branch will take care of it

                String fieldName = parser.getCurrentName();
                if (fieldName.equals(name)) {
                    // Found
                    tb.copyCurrentEvent(parser);
                    expectNextEvent(JsonToken.VALUE_STRING);
                    tb.copyCurrentEvent(parser);

                    return new AbstractMap.SimpleImmutableEntry<>(
                            parser.getText(),
                            new JacksonJsonpParser(JsonParserSequence.createFlattened(false, tb.asParser(), parser))
                    );
                } else {
                    tb.copyCurrentStructure(parser);
                }
            }
            // Copy ending END_OBJECT
            tb.copyCurrentEvent(parser);
        } catch (IOException e) {
            throw JacksonUtils.convertException(e);
        }

        // Field not found
        return new AbstractMap.SimpleImmutableEntry<>(
                defaultValue,
                new JacksonJsonpParser(JsonParserSequence.createFlattened(false, tb.asParser(), parser))
        );
    }

    @Override
    public <Variant> Map.Entry<Variant, JsonParser> findVariant(Map<String, Variant> variants) {
        // We're on a START_OBJECT event
        TokenBuffer tb = new TokenBuffer(parser, null);

        try {
            // The resulting parser must contain the full object, including START_EVENT
            tb.copyCurrentEvent(parser);
            while (parser.nextToken() != JsonToken.END_OBJECT) {

                expectEvent(JsonToken.FIELD_NAME);
                String fieldName = parser.getCurrentName();

                Variant variant = variants.get(fieldName);
                if (variant != null) {
                    tb.copyCurrentEvent(parser);
                    return new AbstractMap.SimpleImmutableEntry<>(
                            variant,
                            new JacksonJsonpParser(JsonParserSequence.createFlattened(false, tb.asParser(), parser))
                    );
                } else {
                    tb.copyCurrentStructure(parser);
                }
            }
            // Copy ending END_OBJECT
            tb.copyCurrentEvent(parser);
        } catch (IOException e) {
            throw JacksonUtils.convertException(e);
        }

        // No variant found: return the buffered parser and let the caller decide what to do.
        return new AbstractMap.SimpleImmutableEntry<>(
                null,
                new JacksonJsonpParser(JsonParserSequence.createFlattened(false, tb.asParser(), parser))
        );
    }

    private void expectNextEvent(JsonToken expected) throws IOException {
        JsonToken event = parser.nextToken();
        if (event != expected) {
            throw new UnexpectedJsonEventException(this, tokenToEvent.get(event), tokenToEvent.get(expected));
        }
    }

    private void expectEvent(JsonToken expected) {
        JsonToken event = parser.currentToken();
        if (event != expected) {
            throw new UnexpectedJsonEventException(this, tokenToEvent.get(event), tokenToEvent.get(expected));
        }
    }
}

