/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os.json.jackson;

import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;

public final class SearchAfterFieldJsonGenerator extends JacksonJsonpGenerator {

  private static final String MIN_LONG_AS_STRING = String.format("%d", Long.MIN_VALUE);
  private static final String SEARCH_AFTER_FIELD = "search_after";

  private final JacksonJsonpGenerator generator;
  private boolean writesSearchAfter;

  public SearchAfterFieldJsonGenerator(final JacksonJsonpGenerator generator) {
    super(generator.jacksonGenerator());
    this.generator = generator;
  }

  @Override
  public JsonGenerator writeKey(String name) {
    if (SEARCH_AFTER_FIELD.equals(name)) {
      writesSearchAfter = true;
    }
    generator.writeKey(name);
    return this;
  }

  @Override
  public JsonGenerator writeEnd() {
    if (writesSearchAfter) {
      // no need anymore to use this
      // generator, can switch back to
      // the actual generator
      writesSearchAfter = false;
      return generator.writeEnd();
    }
    generator.writeEnd();
    return this;
  }

  @Override
  public JsonGenerator write(String value) {
    if (writesSearchAfter && MIN_LONG_AS_STRING.equals(value)) {
      generator.write(Long.MIN_VALUE);
    } else {
      generator.write(value);
    }
    return this;
  }
}
