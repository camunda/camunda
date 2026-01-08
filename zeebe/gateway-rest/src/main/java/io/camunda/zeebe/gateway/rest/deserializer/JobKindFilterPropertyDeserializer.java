/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedJobKindFilter;
import io.camunda.gateway.protocol.model.JobKindEnum;
import io.camunda.gateway.protocol.model.JobKindFilterProperty;

public class JobKindFilterPropertyDeserializer
    extends FilterDeserializer<JobKindFilterProperty, JobKindEnum> {

  @Override
  protected Class<? extends JobKindFilterProperty> getFinalType() {
    return AdvancedJobKindFilter.class;
  }

  @Override
  protected Class<JobKindEnum> getImplicitValueType() {
    return JobKindEnum.class;
  }

  @Override
  protected JobKindFilterProperty createFromImplicitValue(final JobKindEnum value) {
    final var filter = new AdvancedJobKindFilter();
    filter.set$Eq(value);
    return filter;
  }
}
