/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.serde;

import io.avaje.jsonb.Jsonb;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

public final class JsonOutputFormatter implements OutputFormatter {

  private final Jsonb jsonb = Jsonb.builder().build();
  private final BufferedWriter writer;

  public JsonOutputFormatter(final Writer writer) {
    this.writer = new BufferedWriter(writer);
  }

  @Override
  public <T> void write(final T value, final Class<T> type) throws IOException {
    final var serialized = jsonb.type(type).toJson(value);
    writer.write(serialized);
    writer.newLine();
  }

  @Override
  public <T> String serialize(final T value, final Class<T> type) {
    return jsonb.type(type).toJson(value);
  }
}
