/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util;

import io.camunda.operate.schema.IndexMapping;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import java.util.Map;

public interface SchemaTestHelper {

  void dropSchema();

  IndexMapping getTemplateMappings(TemplateDescriptor template);

  void createIndex(IndexDescriptor indexDescriptor, String indexName, String indexSchemaFilename);

  void setReadOnly(String indexName, boolean readOnly);

  Map<String, String> getComponentTemplateSettings(String componentTemplateName);
}
