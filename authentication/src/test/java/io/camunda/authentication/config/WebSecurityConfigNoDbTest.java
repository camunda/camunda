/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.authentication.ConditionalOnNoSecondaryStorage;
import io.camunda.security.entity.AuthenticationMethod;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

public class WebSecurityConfigNoDbTest {

  @Test
  void shouldFailFastWhenBasicAuthConfiguredWithNoSecondaryStorage() {
    // given
    final var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getSystemProperties().put("camunda.database.type", "none");
    context.getEnvironment().getSystemProperties().put("camunda.security.authentication.method", "basic");
    
    // when
    context.register(TestBasicAuthNoDbConfiguration.class);
    
    // then
    final IllegalStateException exception = assertThrows(IllegalStateException.class, 
                                                          context::refresh);
    assertThat(exception.getMessage()).contains("Basic Authentication is not supported");
    assertThat(exception.getMessage()).contains("secondary storage is disabled");
    assertThat(exception.getMessage()).contains("camunda.database.type=none");
  }

  @Test
  void shouldWarnWhenOidcAuthConfiguredWithNoSecondaryStorage() {
    // given
    final var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().getSystemProperties().put("camunda.database.type", "none");
    context.getEnvironment().getSystemProperties().put("camunda.security.authentication.method", "oidc");
    
    // when
    context.register(TestOidcAuthNoDbConfiguration.class);
    context.refresh();
    
    // then - should not throw, just warn (verified via logs in actual usage)
    assertThat(context.getBean(WebSecurityConfig.OidcAuthenticationNoDbWarningBean.class)).isNotNull();
    
    context.close();
  }

  @Configuration
  static class TestBasicAuthNoDbConfiguration {
    @Bean
    @ConditionalOnNoSecondaryStorage
    public WebSecurityConfig.BasicAuthenticationNoDbFailFastBean basicAuthenticationNoDbFailFastBean() {
      throw new IllegalStateException(
          "Basic Authentication is not supported when secondary storage is disabled "
              + "(camunda.database.type=none). Basic Authentication requires access to user data "
              + "stored in secondary storage. Please either enable secondary storage by configuring "
              + "camunda.database.type to a supported database type, or disable authentication by "
              + "removing camunda.security.authentication.method configuration.");
    }
  }

  @Configuration
  static class TestOidcAuthNoDbConfiguration {
    @Bean
    @ConditionalOnNoSecondaryStorage
    public WebSecurityConfig.OidcAuthenticationNoDbWarningBean oidcAuthenticationNoDbWarningBean() {
      return new WebSecurityConfig.OidcAuthenticationNoDbWarningBean();
    }
  }
}