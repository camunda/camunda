/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("fillPostImporterQueue")
public class FillPostImporterQueueStep extends AbstractStep implements DataInitializerStep {

  @Override
  public String toString() {
    return "FillPostImporterQueueStep{} " + super.toString();
  }
}
