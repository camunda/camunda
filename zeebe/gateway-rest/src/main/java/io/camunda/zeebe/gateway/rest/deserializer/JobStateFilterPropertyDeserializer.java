package io.camunda.zeebe.gateway.rest.deserializer;

import io.camunda.zeebe.gateway.protocol.rest.AdvancedJobStateFilter;
import io.camunda.zeebe.gateway.protocol.rest.JobStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.JobStateFilterProperty;

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
