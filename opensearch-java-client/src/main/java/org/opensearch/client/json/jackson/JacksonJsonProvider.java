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

import com.fasterxml.jackson.core.JsonFactory;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;

/**
 * A partial implementation of JSONP's SPI on top of Jackson.
 */
public class JacksonJsonProvider extends JsonProvider {

    private final JsonFactory jsonFactory;

    public JacksonJsonProvider(JsonFactory jsonFactory) {
        this.jsonFactory = jsonFactory;
    }

    public JacksonJsonProvider() {
        this(new JsonFactory());
    }

    /**
     * Return the underlying Jackson {@link JsonFactory}.
     */
    public JsonFactory jacksonJsonFactory() {
        return this.jsonFactory;
    }

    //---------------------------------------------------------------------------------------------
    // Parser

    private final ParserFactory defaultParserFactory = new ParserFactory(null);

    @Override
    public JsonParserFactory createParserFactory(Map<String, ?> config) {
        if (config == null || config.isEmpty()) {
            return defaultParserFactory;
        } else {
            // TODO: handle specific configuration
            return defaultParserFactory;
        }
    }

    @Override
    public JsonParser createParser(Reader reader) {
        return defaultParserFactory.createParser(reader);
    }

    @Override
    public JsonParser createParser(InputStream in) {
        return defaultParserFactory.createParser(in);
    }

    private class ParserFactory implements JsonParserFactory {

        private final Map<String, ?> config;

        ParserFactory(Map<String, ?> config) {
            this.config = config == null ? Collections.emptyMap() : config;
        }

        @Override
        public JsonParser createParser(Reader reader) {
            try {
                return new JacksonJsonpParser(jsonFactory.createParser(reader));
            } catch (IOException ioe) {
                throw JacksonUtils.convertException(ioe);
            }
        }

        @Override
        public JsonParser createParser(InputStream in) {
            try {
                return new JacksonJsonpParser(jsonFactory.createParser(in));
            } catch (IOException ioe) {
                throw JacksonUtils.convertException(ioe);
            }
        }

        @Override
        public JsonParser createParser(InputStream in, Charset charset) {
            try {
                return new JacksonJsonpParser(jsonFactory.createParser(new InputStreamReader(in, charset)));
            } catch (IOException ioe) {
                throw JacksonUtils.convertException(ioe);
            }
        }

        /**
         * Not implemented.
         */
        @Override
        public JsonParser createParser(JsonObject obj) {
            return JsonProvider.provider().createParserFactory(null).createParser(obj);
        }

        /**
         * Not implemented.
         */
        @Override
        public JsonParser createParser(JsonArray array) {
            return JsonProvider.provider().createParserFactory(null).createParser(array);
        }

        /**
         * Not implemented.
         */
        @Override
        public Map<String, ?> getConfigInUse() {
            return config;
        }
    }

    //---------------------------------------------------------------------------------------------
    // Generator

    private final JsonGeneratorFactory defaultGeneratorFactory = new GeneratorFactory(null);

    @Override
    public JsonGeneratorFactory createGeneratorFactory(Map<String, ?> config) {
        if (config == null || config.isEmpty()) {
            return defaultGeneratorFactory;
        } else {
            // TODO: handle specific configuration
            return defaultGeneratorFactory;
        }
    }

    @Override
    public JsonGenerator createGenerator(Writer writer) {
        return defaultGeneratorFactory.createGenerator(writer);
    }

    @Override
    public JsonGenerator createGenerator(OutputStream out) {
        return defaultGeneratorFactory.createGenerator(out);
    }

    private class GeneratorFactory implements JsonGeneratorFactory {

        private final Map<String, ?> config;

        GeneratorFactory(Map<String, ?> config) {
            this.config = config == null ? Collections.emptyMap() : config;
        }

        @Override
        public JsonGenerator createGenerator(Writer writer) {
            try {
                return new JacksonJsonpGenerator(jsonFactory.createGenerator(writer));
            } catch (IOException ioe) {
                throw JacksonUtils.convertException(ioe);
            }
        }

        @Override
        public JsonGenerator createGenerator(OutputStream out) {
            try {
                return new JacksonJsonpGenerator(jsonFactory.createGenerator(out));
            } catch (IOException ioe) {
                throw JacksonUtils.convertException(ioe);
            }
        }

        @Override
        public JsonGenerator createGenerator(OutputStream out, Charset charset) {
            try {
                return new JacksonJsonpGenerator(jsonFactory.createGenerator(new OutputStreamWriter(out, charset)));
            } catch (IOException ioe) {
                throw JacksonUtils.convertException(ioe);
            }

        }

        @Override
        public Map<String, ?> getConfigInUse() {
            return config;
        }
    }

    //---------------------------------------------------------------------------------------------
    // Unsupported operations

    /**
     * Not implemented.
     */
    @Override
    public JsonReader createReader(Reader reader) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented.
     */
    @Override
    public JsonReader createReader(InputStream in) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented.
     */
    @Override
    public JsonWriter createWriter(Writer writer) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented.
     */
    @Override
    public JsonWriter createWriter(OutputStream out) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented.
     */
    @Override
    public JsonWriterFactory createWriterFactory(Map<String, ?> config) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented.
     */
    @Override
    public JsonReaderFactory createReaderFactory(Map<String, ?> config) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented.
     */
    @Override
    public JsonObjectBuilder createObjectBuilder() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented.
     */
    @Override
    public JsonArrayBuilder createArrayBuilder() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not implemented.
     */
    @Override
    public JsonBuilderFactory createBuilderFactory(Map<String, ?> config) {
        throw new UnsupportedOperationException();
    }
}
