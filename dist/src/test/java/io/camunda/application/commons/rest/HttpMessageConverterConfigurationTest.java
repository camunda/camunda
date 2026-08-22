/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

class HttpMessageConverterConfigurationTest {

  private ApplicationContextRunner runnerWithProfiles(final String... profiles) {
    return new ApplicationContextRunner()
        .withUserConfiguration(HttpMessageConverterConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withPropertyValues("spring.profiles.active=" + String.join(",", profiles));
  }

  @Test
  void shouldStartWithoutWebappConvertersWhenSecondaryStorageIsDisabled() {
    // given the operate and tasklist profiles, but no secondary storage: the object mappers the
    // webapp converters depend on come from module configurations that are themselves conditional
    // on secondary storage, so requiring them here would fail the whole context

    // when/then
    runnerWithProfiles("operate", "tasklist")
        .withPropertyValues("camunda.data.secondary-storage.type=none")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean("operateV1MappingJackson2HttpMessageConverter");
              assertThat(context).doesNotHaveBean("tasklistV1MappingJackson2HttpMessageConverter");
              assertThat(context).hasBean("defaultRestMappingJackson2HttpMessageConverter");
            });
  }

  @Test
  void shouldRegisterWebappConvertersWhenSecondaryStorageIsEnabled() {
    // given
    final var operateObjectMapper = new ObjectMapper();
    final var tasklistObjectMapper = new ObjectMapper();

    // when/then
    runnerWithProfiles("operate", "tasklist")
        .withPropertyValues("camunda.data.secondary-storage.type=elasticsearch")
        .withBean("operateObjectMapper", ObjectMapper.class, () -> operateObjectMapper)
        .withBean("tasklistObjectMapper", ObjectMapper.class, () -> tasklistObjectMapper)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .getBean(
                      "operateV1MappingJackson2HttpMessageConverter",
                      MappingJackson2HttpMessageConverter.class)
                  .isNotNull();
              assertThat(context)
                  .getBean(
                      "tasklistV1MappingJackson2HttpMessageConverter",
                      MappingJackson2HttpMessageConverter.class)
                  .isNotNull();
            });
  }
}
