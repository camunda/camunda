/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedUserTaskStateFilter;
import io.camunda.gateway.protocol.model.UserTaskStateEnum;
import io.camunda.gateway.protocol.model.UserTaskStateFilterProperty;

public class UserTaskStateFilterPropertyDeserializer
    extends FilterDeserializer<UserTaskStateFilterProperty, UserTaskStateEnum> {

  @Override
  protected Class<? extends UserTaskStateFilterProperty> getFinalType() {
    return AdvancedUserTaskStateFilter.class;
  }

  @Override
  protected Class<UserTaskStateEnum> getImplicitValueType() {
    return UserTaskStateEnum.class;
  }

  @Override
  protected UserTaskStateFilterProperty createFromImplicitValue(final UserTaskStateEnum value) {
    final var filter = new AdvancedUserTaskStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
