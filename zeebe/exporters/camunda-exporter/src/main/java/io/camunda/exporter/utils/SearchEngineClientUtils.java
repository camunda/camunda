/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
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

public final class SearchEngineClientUtils {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SearchEngineClientUtils() {}

  public static InputStream appendToFileSchemaSettings(
      final InputStream file, final IndexSettings settingsToAppend) throws IOException {

    final var map = MAPPER.readValue(file, new TypeReference<Map<String, Object>>() {});

    final var settingsBlock =
        (Map<String, Object>) map.computeIfAbsent("settings", k -> new HashMap<>());
    final var indexBlock =
        (Map<String, Object>) settingsBlock.computeIfAbsent("index", k -> new HashMap<>());

    indexBlock.put("number_of_shards", settingsToAppend.getNumberOfShards());
    indexBlock.put("number_of_replicas", settingsToAppend.getNumberOfReplicas());

    return new ByteArrayInputStream(MAPPER.writeValueAsBytes(map));
  }

  public static String listIndices(final List<IndexDescriptor> indexDescriptors) {
    return indexDescriptors.stream()
        .map(IndexDescriptor::getFullQualifiedName)
        .collect(Collectors.joining(","));
  }

  public static <T> T mapToSettings(
      final Map<String, String> settingsMap, final Function<InputStream, T> settingsDeserializer) {
    try (final var settingsStream =
        IOUtils.toInputStream(MAPPER.writeValueAsString(settingsMap), StandardCharsets.UTF_8)) {

      return settingsDeserializer.apply(settingsStream);
    } catch (final IOException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to serialise settings in PutSettingsRequest [%s]", settingsMap.toString()),
          e);
    }
  }
}
