/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.UserProfile;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.CamundaUserProvider;
import io.camunda.auth.domain.spi.TenantInfoProvider;
import io.camunda.auth.domain.spi.UserProfileProvider;
import io.camunda.auth.domain.spi.WebComponentAccessProvider;
import io.camunda.auth.spring.user.BasicCamundaUserProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CamundaUserProviderAutoConfigurationTest {

  private static final AutoConfigurations AUTO_CONFIGS =
      AutoConfigurations.of(
          CamundaAuthAutoConfiguration.class, CamundaUserProviderAutoConfiguration.class);

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AUTO_CONFIGS)
          .withUserConfiguration(AllSpiBeansConfiguration.class);

  @Test
  void shouldCreateBasicCamundaUserProviderWhenAllSpisPresent() {
    contextRunner
        .withPropertyValues("camunda.auth.method=basic")
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaUserProvider.class);
              assertThat(context)
                  .getBean(CamundaUserProvider.class)
                  .isInstanceOf(BasicCamundaUserProvider.class);
            });
  }

  @Test
  void shouldNotCreateBasicProviderWithoutUserProfileProvider() {
    new ApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGS)
        .withUserConfiguration(SpiBeansWithoutUserProfileConfiguration.class)
        .withPropertyValues("camunda.auth.method=basic")
        .run(context -> assertThat(context).doesNotHaveBean(CamundaUserProvider.class));
  }

  @Test
  void shouldNotCreateBasicProviderWithoutTenantInfoProvider() {
    new ApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGS)
        .withUserConfiguration(SpiBeansWithoutTenantInfoConfiguration.class)
        .withPropertyValues("camunda.auth.method=basic")
        .run(context -> assertThat(context).doesNotHaveBean(CamundaUserProvider.class));
  }

  @Test
  void shouldNotCreateProviderWhenAuthMethodPropertyMissing() {
    new ApplicationContextRunner()
        .withConfiguration(AUTO_CONFIGS)
        .run(context -> assertThat(context).doesNotHaveBean(CamundaUserProvider.class));
  }

  @Test
  void shouldBackOffWhenCustomProviderExists() {
    contextRunner
        .withPropertyValues("camunda.auth.method=basic")
        .withUserConfiguration(CustomUserProviderConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CamundaUserProvider.class);
              assertThat(context)
                  .getBean(CamundaUserProvider.class)
                  .isInstanceOf(TestCamundaUserProvider.class);
            });
  }

  /** All SPI beans present — the happy path for basic auth. */
  @Configuration(proxyBeanMethods = false)
  static class AllSpiBeansConfiguration {
    @Bean
    CamundaAuthenticationProvider testAuthenticationProvider() {
      return () -> CamundaAuthentication.anonymous();
    }

    @Bean
    WebComponentAccessProvider testWebComponentAccessProvider() {
      return new TestWebComponentAccessProvider();
    }

    @Bean
    UserProfileProvider testUserProfileProvider() {
      return username -> new UserProfile(username, username + "@test.com");
    }

    @Bean
    TenantInfoProvider testTenantInfoProvider() {
      return tenantIds -> List.of();
    }
  }

  /** Missing UserProfileProvider — basic provider should NOT be created. */
  @Configuration(proxyBeanMethods = false)
  static class SpiBeansWithoutUserProfileConfiguration {
    @Bean
    CamundaAuthenticationProvider authProvider() {
      return () -> CamundaAuthentication.anonymous();
    }

    @Bean
    WebComponentAccessProvider webComponentProvider() {
      return new TestWebComponentAccessProvider();
    }

    @Bean
    TenantInfoProvider tenantProvider() {
      return tenantIds -> List.of();
    }
  }

  /** Missing TenantInfoProvider — basic provider should NOT be created. */
  @Configuration(proxyBeanMethods = false)
  static class SpiBeansWithoutTenantInfoConfiguration {
    @Bean
    CamundaAuthenticationProvider authProvider() {
      return () -> CamundaAuthentication.anonymous();
    }

    @Bean
    WebComponentAccessProvider webComponentProvider() {
      return new TestWebComponentAccessProvider();
    }

    @Bean
    UserProfileProvider userProfileProvider() {
      return username -> null;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomUserProviderConfiguration {
    @Bean
    CamundaUserProvider customCamundaUserProvider() {
      return new TestCamundaUserProvider();
    }
  }

  static class TestCamundaUserProvider implements CamundaUserProvider {
    @Override
    public io.camunda.auth.domain.model.CamundaUserInfo getCurrentUser() {
      return null;
    }

    @Override
    public String getUserToken() {
      return null;
    }
  }

  static class TestWebComponentAccessProvider implements WebComponentAccessProvider {
    @Override
    public boolean isAuthorizationEnabled() {
      return false;
    }

    @Override
    public boolean hasAccessToComponent(
        final CamundaAuthentication authentication, final String component) {
      return true;
    }

    @Override
    public List<String> getAuthorizedComponents(final CamundaAuthentication authentication) {
      return List.of();
    }
  }
}
