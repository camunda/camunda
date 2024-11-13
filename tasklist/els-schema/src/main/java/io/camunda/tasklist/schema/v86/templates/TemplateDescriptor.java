/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.templates;

import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public interface TemplateDescriptor extends IndexDescriptor {

  String PARTITION_ID = "partitionId";

  default String getTemplateName() {
    return getFullQualifiedName() + "template";
  }

  default String getIndexPattern() {
    return getFullQualifiedName() + "*";
  }
}
