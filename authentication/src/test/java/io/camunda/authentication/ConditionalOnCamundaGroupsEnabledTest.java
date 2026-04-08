/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ConditionalOnCamundaGroupsEnabledTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @Test
  void shouldEnableGroupsBeanWhenGroupsClaimNotSet() {
    // given/when/then
    contextRunner
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(GroupsEnabledBean.class);
              assertThat(context).doesNotHaveBean(GroupsDisabledBean.class);
            });
  }

  @ParameterizedTest(name = "property key: {0}")
  @ValueSource(
      strings = {
        "camunda.security.authentication.oidc.groups-claim",
        "camunda.security.authentication.oidc.groupsClaim"
      })
  void shouldDisableGroupsBeanWhenGroupsClaimIsSet(final String propertyKey) {
    // given/when/then
    contextRunner
        .withPropertyValues(propertyKey + "=$.groups")
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(GroupsEnabledBean.class);
              assertThat(context).hasSingleBean(GroupsDisabledBean.class);
            });
  }

  @Configuration
  static class TestConfig {
    @Bean
    @ConditionalOnCamundaGroupsEnabled
    GroupsEnabledBean groupsEnabledBean() {
      return new GroupsEnabledBean();
    }

    @Bean
    @ConditionalOnCamundaGroupsDisabled
    GroupsDisabledBean groupsDisabledBean() {
      return new GroupsDisabledBean();
    }
  }

  static class GroupsEnabledBean {}

  static class GroupsDisabledBean {}
}
