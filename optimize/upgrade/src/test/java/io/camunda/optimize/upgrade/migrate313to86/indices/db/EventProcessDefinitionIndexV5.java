/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86.indices.db;

import io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;

public abstract class EventProcessDefinitionIndexV5<TBuilder>
    extends ProcessDefinitionIndex<TBuilder> {

  public static final int VERSION = 5;

  @Override
  public String getIndexName() {
    return "event-process-definition";
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
