/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;

public record PutIndexTemplateResponse(boolean acknowledged) {

  public static final JsonpDeserializer<PutIndexTemplateResponse> _DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          PutIndexTemplateResponse.Builder::new, PutIndexTemplateResponse::setupDeserializer);

  private static void setupDeserializer(final ObjectDeserializer<Builder> deserializer) {
    deserializer.add(
        PutIndexTemplateResponse.Builder::acknowledged,
        JsonpDeserializer.booleanDeserializer(),
        "acknowledged");
  }

  static class Builder implements ObjectBuilder<PutIndexTemplateResponse> {
    private Boolean acknowledged;

    public PutIndexTemplateResponse.Builder acknowledged(final Boolean acknowledged) {
      this.acknowledged = acknowledged;
      return this;
    }

    @Override
    public PutIndexTemplateResponse build() {
      return new PutIndexTemplateResponse(Boolean.TRUE.equals(acknowledged));
    }
  }
}
