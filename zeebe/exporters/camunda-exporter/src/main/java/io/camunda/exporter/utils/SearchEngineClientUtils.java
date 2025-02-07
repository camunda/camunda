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
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
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

  public InputStream appendToFileSchemaSettings(
      final InputStream file, final IndexSettings settingsToAppend) throws IOException {

    final var map = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});

    final var settingsBlock =
        (Map<String, Object>) map.computeIfAbsent("settings", k -> new HashMap<>());
    final var indexBlock =
        (Map<String, Object>) settingsBlock.computeIfAbsent("index", k -> new HashMap<>());

    indexBlock.put("number_of_shards", settingsToAppend.getNumberOfShards());
    indexBlock.put("number_of_replicas", settingsToAppend.getNumberOfReplicas());

    return new ByteArrayInputStream(objectMapper.writeValueAsBytes(map));
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

  public boolean allImportersCompleted(
      final List<ImportPositionEntity> recordReaderStatuses, final int totalValueTypesCount) {
    // For a fresh install where there are no import position documents
    if (recordReaderStatuses.isEmpty()) {
      return true;
    }

    final var partitionCompleted =
        recordReaderStatuses.stream().anyMatch(ImportPositionEntity::getCompleted);

    // If all record readers have an import position document we can check their completed values

    if (recordReaderStatuses.size() == totalValueTypesCount) {
      return recordReaderStatuses.stream().allMatch(ImportPositionEntity::getCompleted);
    }

    // If some record readers are missing their import position document we can say their status
    // is equal to that of the partition completion status.

    return recordReaderStatuses.stream().allMatch(ImportPositionEntity::getCompleted)
        && partitionCompleted;
  }
}
