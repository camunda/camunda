/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.gateway.protocol.model.AdvancedJobStateFilter;
import io.camunda.gateway.protocol.model.JobStateEnum;
import io.camunda.gateway.protocol.model.JobStateFilterProperty;

public class JobStateFilterPropertyDeserializer
    extends FilterDeserializer<JobStateFilterProperty, JobStateEnum> {

  @Override
  protected Class<? extends JobStateFilterProperty> getFinalType() {
    return AdvancedJobStateFilter.class;
  }

  @Override
  protected Class<JobStateEnum> getImplicitValueType() {
    return JobStateEnum.class;
  }

  @Override
  protected JobStateFilterProperty createFromImplicitValue(final JobStateEnum value) {
    final var filter = new AdvancedJobStateFilter();
    filter.set$Eq(value);
    return filter;
  }
}
