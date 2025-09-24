package io.camunda.client;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;

public class ResourceConfig {
  public static Resource create() {
    return Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, "camunda"));
  }
}
