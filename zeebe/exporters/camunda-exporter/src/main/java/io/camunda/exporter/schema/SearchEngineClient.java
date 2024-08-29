/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import io.camunda.exporter.NoopExporterConfiguration.IndexSpecificSettings;
import io.camunda.exporter.schema.descriptors.ComponentTemplateDescriptor;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;

public interface SearchEngineClient {
  void createIndex(final IndexDescriptor indexDescriptor);

  void createIndexTemplate(
      final IndexTemplateDescriptor indexDescriptor, final IndexSpecificSettings settings);

  void createComponentTemplate(final ComponentTemplateDescriptor templateDescriptor);

  /**
   * The {@code propertiesJson} should have a root properties field, for example:
   *
   * <pre>{@code
   * {
   *   "properties" : {
   *     "field" : {
   *       "type" : "keyword"
   *     }
   *   }
   * }
   * }</pre>
   *
   * @param indexDescriptor Representing index of which to update the mappings
   * @param propertiesJson New properties to be appended to the index
   */
  void putMapping(final IndexDescriptor indexDescriptor, final String propertiesJson);
}
