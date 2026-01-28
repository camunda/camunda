/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedJobListenerEventTypeFilter;
import io.camunda.gateway.protocol.model.JobListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.JobListenerEventTypeFilterProperty;

public class JobListenerEventTypeFilterPropertyDeserializer
    extends FilterDeserializer<JobListenerEventTypeFilterProperty, JobListenerEventTypeEnum> {

  @Override
  protected Class<? extends JobListenerEventTypeFilterProperty> getFinalType() {
    return AdvancedJobListenerEventTypeFilter.class;
  }

  @Override
  protected Class<JobListenerEventTypeEnum> getImplicitValueType() {
    return JobListenerEventTypeEnum.class;
  }

  @Override
  protected AdvancedJobListenerEventTypeFilter createFromImplicitValue(
      final JobListenerEventTypeEnum value) {
    final var filter = new AdvancedJobListenerEventTypeFilter();
    filter.set$Eq(value);
    return filter;
  }
}
