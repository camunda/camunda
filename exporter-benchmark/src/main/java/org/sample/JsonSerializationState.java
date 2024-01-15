/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.json.OperateEntityFactory;
import io.camunda.zeebe.exporter.operate.NoSpringJacksonConfig;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class JsonSerializationState {

  private OperateEntityFactory entityFactory;
  private ObjectMapper objectMapper;

  public JsonSerializationState() {
    this.entityFactory = new OperateEntityFactory();
    this.objectMapper = NoSpringJacksonConfig.buildObjectMapper();
  }

  public OperateEntityFactory getEntityFactory() {
    return entityFactory;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }
}
