/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.ProcessDefinitionNameFilter;
import io.camunda.gateway.protocol.model.ProcessDefinitionNameFilterProperty;

public class ProcessDefinitionNameFilterPropertyDeserializer
    extends FilterDeserializer<ProcessDefinitionNameFilterProperty, String> {

  @Override
  protected Class<? extends ProcessDefinitionNameFilterProperty> getFinalType() {
    return ProcessDefinitionNameFilter.class;
  }

  @Override
  protected Class<String> getImplicitValueType() {
    return String.class;
  }

  @Override
  protected ProcessDefinitionNameFilterProperty createFromImplicitValue(final String value) {
    return ProcessDefinitionNameFilter.Builder.create().$eq(value).build();
  }
}
