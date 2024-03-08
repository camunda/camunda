package io.camunda.zeebe.spring.client.annotation.customizer;

import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;

/**
 * This interface could be used to customize the {@link
 * io.camunda.zeebe.spring.client.annotation.ZeebeWorker} annotation's values. Just implement it and
 * put it into to the {@link org.springframework.context.ApplicationContext} to make it work. But be
 * careful: these customizers are applied sequentially and if you need to change the order of these
 * customizers use the {@link org.springframework.core.annotation.Order} annotation or the {@link
 * org.springframework.core.Ordered} interface.
 *
 * @see io.camunda.zeebe.spring.client.properties.PropertyBasedZeebeWorkerValueCustomizer
 */
public interface ZeebeWorkerValueCustomizer {

  void customize(final ZeebeWorkerValue zeebeWorker);
}
