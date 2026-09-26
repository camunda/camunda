/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.operate.webapp.api.v1.dao.VariableDao;
import io.camunda.operate.webapp.api.v1.rest.VariableByKeyController;
import io.camunda.operate.webapp.api.v1.rest.VariableController;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.tasklist.webapp.api.rest.v1.controllers.VariablesController;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.service.VariableService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Fast (no Docker, no broker) reproduction of camunda#59478: with both Operate's and Tasklist's V1
 * variable-by-id controllers registered into one {@link RequestMappingHandlerMapping} — the exact
 * shape of {@code StandaloneCamunda}'s single Spring context — resolving {@code GET
 * /v1/variables/1} is ambiguous unless one side opts out via its {@code @ConditionalOnProperty}
 * gate.
 */
class VariablesV1AmbiguousMappingHandlerMappingTest {

  private static final String TASKLIST_PROPERTY = "camunda.tasklist.v1-variable-by-id-enabled";
  private static final String OPERATE_PROPERTY = "camunda.operate.v1-variable-by-key-enabled";

  // Registered as raw singletons (not via withBean(Class, Supplier)) so that Spring never runs
  // field-autowiring post-processing on the mocks themselves - VariableService in particular
  // carries its own @Autowired fields (TaskStore, VariableStore, ...) that would otherwise need
  // mocking transitively just to satisfy the bean lifecycle, despite being irrelevant to what
  // this test verifies (route resolution, not service behavior).
  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class))
          .withInitializer(
              context -> {
                final var beanFactory = context.getBeanFactory();
                beanFactory.registerSingleton("variableDao", mock(VariableDao.class));
                beanFactory.registerSingleton("permissionsService", mock(PermissionsService.class));
                beanFactory.registerSingleton("variableService", mock(VariableService.class));
                beanFactory.registerSingleton(
                    "tasklistPermissionServices", mock(TasklistPermissionServices.class));
              })
          .withUserConfiguration(
              VariableController.class, VariableByKeyController.class, VariablesController.class);

  @Test
  void shouldBeAmbiguousByDefault() {
    contextRunner.run(
        context -> {
          final var handlerMapping = context.getBean(RequestMappingHandlerMapping.class);
          final var request = new MockHttpServletRequest("GET", "/v1/variables/1");

          assertThatThrownBy(() -> handlerMapping.getHandler(request))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("Ambiguous handler methods mapped");
        });
  }

  @Test
  void shouldResolveToOperateWhenTasklistOptsOut() {
    contextRunner
        .withPropertyValues(TASKLIST_PROPERTY + "=false")
        .run(
            context -> {
              final var handlerMapping = context.getBean(RequestMappingHandlerMapping.class);
              final var request = new MockHttpServletRequest("GET", "/v1/variables/1");

              final var handler = handlerMapping.getHandler(request);

              assertThat(handler).isNotNull();
              assertThat(handler.getHandler().toString()).contains("VariableByKeyController");
            });
  }

  @Test
  void shouldResolveToTasklistWhenOperateOptsOut() {
    contextRunner
        .withPropertyValues(OPERATE_PROPERTY + "=false")
        .run(
            context -> {
              final var handlerMapping = context.getBean(RequestMappingHandlerMapping.class);
              final var request = new MockHttpServletRequest("GET", "/v1/variables/1");

              final var handler = handlerMapping.getHandler(request);

              assertThat(handler).isNotNull();
              assertThat(handler.getHandler().toString()).contains("VariablesController");
            });
  }

  @Test
  void shouldHaveNoHandlerWhenBothOptOut() {
    contextRunner
        .withPropertyValues(TASKLIST_PROPERTY + "=false", OPERATE_PROPERTY + "=false")
        .run(
            context -> {
              final var handlerMapping = context.getBean(RequestMappingHandlerMapping.class);
              final var request = new MockHttpServletRequest("GET", "/v1/variables/1");

              assertThat(handlerMapping.getHandler(request)).isNull();
            });
  }
}
