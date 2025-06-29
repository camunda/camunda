package io.camunda.client.impl.search.filter.builder;

import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.filter.builder.ListenerEventTypeProperty;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.CollectionUtil;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.JobListenerEventTypeEnum;
import io.camunda.client.protocol.rest.JobListenerEventTypeFilterProperty;
import java.util.List;
import java.util.stream.Collectors;

public class ListenerEventTypePropertyImpl
    extends TypedSearchRequestPropertyProvider<JobListenerEventTypeFilterProperty>
    implements ListenerEventTypeProperty {

  private final JobListenerEventTypeFilterProperty filterProperty =
      new JobListenerEventTypeFilterProperty();

  @Override
  public ListenerEventTypeProperty eq(final ListenerEventType value) {
    filterProperty.set$Eq(EnumUtil.convert(value, JobListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public ListenerEventTypeProperty neq(final ListenerEventType value) {
    filterProperty.set$Neq(EnumUtil.convert(value, JobListenerEventTypeEnum.class));
    return this;
  }

  @Override
  public ListenerEventTypeProperty exists(final boolean value) {
    filterProperty.set$Exists(value);
    return this;
  }

  @Override
  public ListenerEventTypeProperty in(final List<ListenerEventType> value) {
    filterProperty.set$In(
        value.stream()
            .map(source -> (EnumUtil.convert(source, JobListenerEventTypeEnum.class)))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public ListenerEventTypeProperty in(final ListenerEventType... values) {
    return in(CollectionUtil.toList(values));
  }

  @Override
  protected JobListenerEventTypeFilterProperty getSearchRequestProperty() {
    return filterProperty;
  }

  @Override
  public ListenerEventTypeProperty like(final String value) {
    filterProperty.set$Like(value);
    return this;
  }
}
