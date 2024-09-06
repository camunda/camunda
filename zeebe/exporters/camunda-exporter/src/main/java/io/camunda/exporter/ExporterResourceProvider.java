/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;

public interface ExporterResourceProvider {

  /**
   * This should return descriptors describing the desired state of all indices provided.
   *
   * @return A {@link List} of {@link IndexDescriptor}
   */
  List<IndexDescriptor> getIndexDescriptors();

  /**
   * This should return descriptors describing the desired state of all index templates provided.
   *
   * @return A {@link List} of {@link IndexTemplateDescriptor}
   */
  List<IndexTemplateDescriptor> getIndexTemplateDescriptors();
}
