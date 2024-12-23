/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.annotation.customizer;

import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.properties.PropertyBasedJobWorkerValueCustomizer;

/**
 * This interface could be used to customize the {@link
 * io.camunda.spring.client.annotation.JobWorker} annotation's values. Just implement it and put it
 * into to the {@link org.springframework.context.ApplicationContext} to make it work. But be
 * careful: these customizers are applied sequentially and if you need to change the order of these
 * customizers use the {@link org.springframework.core.annotation.Order} annotation or the {@link
 * org.springframework.core.Ordered} interface.
 *
 * @see PropertyBasedJobWorkerValueCustomizer
 */
public interface JobWorkerValueCustomizer {

  void customize(final JobWorkerValue zeebeWorker);
}
