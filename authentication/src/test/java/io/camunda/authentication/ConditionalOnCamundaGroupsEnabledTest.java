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
        .run(context -> assertThat(context).hasSingleBean(GroupsEnabledBean.class));
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
        .withPropertyValues(
            "camunda.security.authentication.method=oidc", propertyKey + "=$.groups")
        .withUserConfiguration(TestConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(GroupsEnabledBean.class));
  }

  @Test
  void shouldEnableGroupsBeanWhenANamedProviderHasNoGroupsClaim() {
    // given one provider that reads groups from a claim and one that does not
    contextRunner
        .withPropertyValues(
            "camunda.security.authentication.method=oidc",
            "camunda.security.authentication.providers.oidc.keycloak.groups-claim=groups",
            "camunda.security.authentication.providers.oidc.partner.client-id=partner")
        .withUserConfiguration(TestConfig.class)
        // then the partner's users need Camunda's group management
        .run(context -> assertThat(context).hasSingleBean(GroupsEnabledBean.class));
  }

  @Test
  void shouldDisableGroupsBeanWhenTheOnlyNamedProviderHasAGroupsClaim() {
    contextRunner
        .withPropertyValues(
            "camunda.security.authentication.method=oidc",
            "camunda.security.authentication.providers.oidc.keycloak.groups-claim=groups")
        .withUserConfiguration(TestConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(GroupsEnabledBean.class));
  }

  @Test
  void shouldEnableGroupsBeanWithBasicAuthenticationWhenANamedProviderHasAGroupsClaim() {
    contextRunner
        .withPropertyValues(
            "camunda.security.authentication.method=basic",
            "camunda.security.authentication.providers.oidc.keycloak.groups-claim=groups")
        .withUserConfiguration(TestConfig.class)
        .run(context -> assertThat(context).hasSingleBean(GroupsEnabledBean.class));
  }

  @Test
  void shouldDisableGroupsBeanWhenNamedProvidersInheritTheGroupsClaimOfTheDefaultProvider() {
    contextRunner
        .withPropertyValues(
            "camunda.security.authentication.method=oidc",
            "camunda.security.authentication.oidc.client-id=default",
            "camunda.security.authentication.oidc.groups-claim=groups",
            "camunda.security.authentication.providers.oidc.partner.client-id=partner")
        .withUserConfiguration(TestConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(GroupsEnabledBean.class));
  }

  @Configuration
  static class TestConfig {
    @Bean
    @ConditionalOnCamundaGroupsEnabled
    GroupsEnabledBean groupsEnabledBean() {
      return new GroupsEnabledBean();
    }
  }

  static class GroupsEnabledBean {}
}
