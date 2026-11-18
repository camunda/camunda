/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Set counterpart of {@link ExporterConfigurationListDeserializer}. Reads the same Spring Boot
 * shapes into a {@link LinkedHashSet}; the list deserializer rejects other tokens.
 */
@NullMarked
final class ExporterConfigurationSetDeserializer<E> extends JsonDeserializer<Set<E>>
    implements ContextualDeserializer {

  private final ExporterConfigurationListDeserializer<E> listDeserializer;

  public ExporterConfigurationSetDeserializer() {
    this(new ExporterConfigurationListDeserializer<>());
  }

  private ExporterConfigurationSetDeserializer(
      final ExporterConfigurationListDeserializer<E> listDeserializer) {
    this.listDeserializer = listDeserializer;
  }

  @Override
  public ExporterConfigurationSetDeserializer<E> createContextual(
      final DeserializationContext ctxt, final @Nullable BeanProperty property)
      throws JsonMappingException {
    return new ExporterConfigurationSetDeserializer<>(
        listDeserializer.createContextual(ctxt, property));
  }

  @Override
  public Set<E> deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    return new LinkedHashSet<>(listDeserializer.deserialize(p, ctxt));
  }
}
