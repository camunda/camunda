/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.exceptions.ElasticsearchExporterException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is the class where teams should make their components such as handlers, and index/index
 * template descriptors available
 */
public class DefaultExporterResourceProvider implements ExporterResourceProvider {

  private static final String POLICY_LIST_FILE = "elasticsearch/policies/policies.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Set<IndexDescriptor> getIndexDescriptors() {
    return Set.of();
  }

  @Override
  public Set<IndexTemplateDescriptor> getIndexTemplateDescriptors() {
    return Set.of();
  }

  @Override
  public Map<String, String> getIndexLifeCyclePolicies() {
    final var policiesFile =
        Thread.currentThread().getContextClassLoader().getResource(POLICY_LIST_FILE);

    if (policiesFile == null) {
      throw new ElasticsearchExporterException(
          String.format("Policy list file [%s] does not exist", POLICY_LIST_FILE));
    }

    final var file = new File(policiesFile.getFile());
    try {
      final List<Map<String, String>> policies = MAPPER.readValue(file, new TypeReference<>() {});
      return policies.stream().collect(Collectors.toMap(m -> m.get("name"), m -> m.get("file")));

    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          String.format("Failed to parse policy list file [%s]", POLICY_LIST_FILE), e);
    }
  }
}
