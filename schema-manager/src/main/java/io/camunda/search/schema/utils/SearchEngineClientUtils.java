/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;

public class SearchEngineClientUtils {
  private final ObjectMapper objectMapper;

  public SearchEngineClientUtils(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String listIndices(final List<IndexDescriptor> indexDescriptors) {
    return indexDescriptors.stream()
        .map(IndexDescriptor::getFullQualifiedName)
        .collect(Collectors.joining(","));
  }

  public <T> T mapToSettings(
      final Map<String, String> settingsMap, final Function<InputStream, T> settingsDeserializer) {
    try (final var settingsStream =
        IOUtils.toInputStream(
            objectMapper.writeValueAsString(settingsMap), StandardCharsets.UTF_8)) {

      return settingsDeserializer.apply(settingsStream);
    } catch (final IOException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to serialise settings in PutSettingsRequest [%s]", settingsMap.toString()),
          e);
    }
  }

  public class SchemaSettingsAppender {
    private final Map<String, Object> map;
    private final Map<String, Object> settingsBlock;
    private final Map<String, Object> indexBlock;

    /**
     * Reads the settings block in {@code file} and writes additional settings using a builder
     * syntax.
     *
     * @param file whose settings block will be read and appended.
     * @throws IOException if unable to parse the given file into a map.
     */
    public SchemaSettingsAppender(final InputStream file) throws IOException {
      map = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});
      settingsBlock = (Map<String, Object>) map.computeIfAbsent("settings", k -> new HashMap<>());
      indexBlock =
          (Map<String, Object>) settingsBlock.computeIfAbsent("index", k -> new HashMap<>());
    }

    public SchemaSettingsAppender withNumberOfShards(final int numberOfShards) {
      indexBlock.put("number_of_shards", numberOfShards);
      return this;
    }

    public SchemaSettingsAppender withNumberOfReplicas(final int numberOfReplicas) {
      indexBlock.put("number_of_replicas", numberOfReplicas);
      return this;
    }

    public InputStream build() throws IOException {
      return new ByteArrayInputStream(objectMapper.writeValueAsBytes(map));
    }
  }
}
