/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;

public final class TestUtil {

  private TestUtil() {}

  public static IndexTemplateDescriptor mockIndexTemplate(
      final String indexName,
      final String indexPattern,
      final String alias,
      final List<String> composedOf,
      final String templateName,
      final String mappingsFileName) {
    final var descriptor = mock(IndexTemplateDescriptor.class);
    when(descriptor.getIndexName()).thenReturn(indexName);
    when(descriptor.getIndexPattern()).thenReturn(indexPattern);
    when(descriptor.getAlias()).thenReturn(alias);
    when(descriptor.getComposedOf()).thenReturn(composedOf);
    when(descriptor.getTemplateName()).thenReturn(templateName);
    when(descriptor.getMappingsClasspathFilename()).thenReturn(mappingsFileName);

    return descriptor;
  }

  public static IndexDescriptor mockIndex(
      final String fullQualifiedName,
      final String alias,
      final String indexName,
      final String mappingsFileName) {
    final var descriptor = mock(IndexDescriptor.class);
    when(descriptor.getFullQualifiedName()).thenReturn(fullQualifiedName);
    when(descriptor.getAlias()).thenReturn(alias);
    when(descriptor.getIndexName()).thenReturn(indexName);
    when(descriptor.getMappingsClasspathFilename()).thenReturn(mappingsFileName);
    when(descriptor.getAllVersionsIndexNameRegexPattern()).thenReturn(fullQualifiedName + ".*");

    return descriptor;
  }
}
