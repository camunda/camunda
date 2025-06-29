package io.camunda.client.impl.search.filter.builder;

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.filter.builder.JobKindProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.JobKindEnum;
import io.camunda.client.protocol.rest.JobKindFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class JobKindPropertyImpl extends TypedSearchRequestPropertyProvider<JobKindFilterProperty>
    implements JobKindProperty {

  private final JobKindFilterProperty filterProperty = new JobKindFilterProperty();

  @Override
  public JobKindProperty eq(final JobKind value) {
    filterProperty.set$Eq(EnumUtil.convert(value, JobKindEnum.class));
    return this;
  }

  @Override
  public JobKindProperty neq(final JobKind value) {
    filterProperty.set$Neq(EnumUtil.convert(value, JobKindEnum.class));
    return this;
  }

  @Override
  public JobKindProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public JobKindProperty in(final List<JobKind> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> (EnumUtil.convert(source, JobKindEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public JobKindProperty in(final JobKind... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected JobKindFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public JobKindProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
