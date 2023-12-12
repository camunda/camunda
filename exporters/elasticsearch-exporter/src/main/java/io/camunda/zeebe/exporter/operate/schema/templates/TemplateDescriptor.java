/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate.schema.templates;

import io.camunda.zeebe.exporter.operate.schema.indices.IndexDescriptor;

public interface TemplateDescriptor extends IndexDescriptor {

  String PARTITION_ID = "partitionId";

  default String getTemplateName() {
    return getFullQualifiedName() + "template";
  }

  default String getIndexPattern() {
    return getFullQualifiedName() + "*";
  }
}
