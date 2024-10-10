/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.Set;

public interface ExporterResourceProvider {

  void init(ExporterConfiguration configuration);

  /**
   * This should return descriptors describing the desired state of all indices provided.
   *
   * @return A {@link Set} of {@link IndexDescriptor}
   */
  Set<IndexDescriptor> getIndexDescriptors();

  /**
   * This should return descriptors describing the desired state of all index templates provided.
   *
   * @return A {@link Set} of {@link IndexTemplateDescriptor}
   */
  Set<IndexTemplateDescriptor> getIndexTemplateDescriptors();
}
