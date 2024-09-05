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

public class DefaultExporterComponentsProvider implements ExporterComponentsProvider {

  @Override
  public List<IndexDescriptor> getIndexDescriptors() {
    return List.of();
  }

  @Override
  public List<IndexTemplateDescriptor> getIndexTemplateDescriptors() {
    return List.of();
  }
}
