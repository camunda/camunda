/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.serde;

import io.avaje.jsonb.Jsonb;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.impl.search.response.ProcessDefinitionImpl;
import io.camunda.client.impl.search.response.ProcessInstanceImpl;
import io.camunda.zeebe.zbctl.json.adapter.ProcessDefinitionImplJsonAdapter;
import io.camunda.zeebe.zbctl.json.adapter.ProcessInstanceImplJsonAdapter;
import io.camunda.zeebe.zbctl.json.adapter.SearchQueryResponseJsonAdapter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

public final class JsonOutputFormatter implements OutputFormatter {

  private final Jsonb jsonb =
      Jsonb.builder()
          .add(SearchQueryResponse.class, SearchQueryResponseJsonAdapter::new)
          .add(ProcessDefinitionImpl.class, ProcessDefinitionImplJsonAdapter::new)
          .add(ProcessInstanceImpl.class, ProcessInstanceImplJsonAdapter::new)
          .build();
  private final BufferedWriter writer;

  public JsonOutputFormatter(final Writer writer) {
    this.writer = new BufferedWriter(writer);
  }

  @Override
  public <T> void write(final T value, final Class<T> type) throws IOException {
    final var serialized = jsonb.type(type).toJson(value);
    writer.write(serialized);
    writer.newLine();
    writer.close();
  }

  @Override
  public <T> String serialize(final T value, final Class<T> type) {
    return jsonb.type(type).toJson(value);
  }
}
