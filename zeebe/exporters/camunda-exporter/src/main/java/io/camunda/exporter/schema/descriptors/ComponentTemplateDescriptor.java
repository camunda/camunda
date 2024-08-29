/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema.descriptors;

public interface ComponentTemplateDescriptor {
  String getTemplateJson();

  String getTemplateName();

  /**
   * Maps to the create query parameter, if true the corresponding request cannot replace or update
   * an existing component template, defaults to false
   *
   * @return The component template request is create only
   */
  default Boolean create() {
    return false;
  }
}
