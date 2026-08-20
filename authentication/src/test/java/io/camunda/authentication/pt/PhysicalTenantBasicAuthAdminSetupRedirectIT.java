/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.config.spi.AdminUserPresenceAdapter;
import io.camunda.authentication.config.spi.SecurityPathAdapter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.core.port.out.AdminUserPresencePort;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityConfiguration;
import io.camunda.security.spring.handler.AuthFailureHandlerConfiguration;
import io.camunda.security.spring.security.AdminUserCheckFilterConfiguration;
import io.camunda.security.spring.security.BaseSecurityConfiguration;
import io.camunda.security.spring.security.BasicAuthWebappSecurityConfiguration;
import io.camunda.security.spring.security.ScopedWebappSecurityChainBuilderConfiguration;
import io.camunda.service.RoleServices;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Proves that {@code AdminUserCheckFilter} consults the request's PT's role store in BASIC mode: a
 * PT without an admin is redirected to {@code /admin/setup}; a PT with one (and the default tenant)
 * passes through.
 */
class PhysicalTenantBasicAuthAdminSetupRedirectIT {

  private static final String ADMIN_ROLE_ID = DefaultRole.ADMIN.getId();
  private static final String PT_WITH_ADMIN = "ptwithadmin";
  private static final String PT_WITHOUT_ADMIN = "ptwithoutadmin";

  private static final RoleServices PT_WITH_ADMIN_ROLE_SERVICES = mock(RoleServices.class);
  private static final RoleServices PT_WITHOUT_ADMIN_ROLE_SERVICES = mock(RoleServices.class);

  @BeforeAll
  static void setUpMocks() {
    when(PT_WITH_ADMIN_ROLE_SERVICES.hasMembersOfType(
            eq(ADMIN_ROLE_ID), eq(EntityType.USER), any(CamundaAuthentication.class)))
        .thenReturn(true);
    when(PT_WITHOUT_ADMIN_ROLE_SERVICES.hasMembersOfType(
            eq(ADMIN_ROLE_ID), eq(EntityType.USER), any(CamundaAuthentication.class)))
        .thenReturn(false);
  }

  @Test
  void shouldRedirectToSetupWhenPtHasNoAdmin() {
    buildRunner()
        .run(
            ctx -> {
              final var proxy = buildProxy(ctx);
              final var request = new MockHttpServletRequest("GET", "/operate/dashboard");
              PhysicalTenantContext.setPhysicalTenantId(request, PT_WITHOUT_ADMIN);
              final var response = new MockHttpServletResponse();

              RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
              try {
                proxy.doFilter(request, response, new MockFilterChain());
              } finally {
                RequestContextHolder.resetRequestAttributes();
              }

              assertThat(response.getStatus()).isEqualTo(302);
              assertThat(response.getRedirectedUrl()).endsWith("/admin/setup");
            });
  }

  @Test
  void shouldNotRedirectWhenPtHasAdmin() {
    buildRunner()
        .run(
            ctx -> {
              final var proxy = buildProxy(ctx);
              final var request = new MockHttpServletRequest("GET", "/operate/dashboard");
              PhysicalTenantContext.setPhysicalTenantId(request, PT_WITH_ADMIN);
              final var response = new MockHttpServletResponse();
              final var downstream = new MockFilterChain();

              RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
              try {
                proxy.doFilter(request, response, downstream);
              } finally {
                RequestContextHolder.resetRequestAttributes();
              }

              assertThat(response.getStatus()).isEqualTo(200);
              assertThat(downstream.getRequest()).isNotNull(); // request reached downstream
            });
  }

  @Test
  void shouldNotRedirectForDefaultTenantWhenAdminPresent() {
    buildRunner()
        .run(
            ctx -> {
              final var proxy = buildProxy(ctx);
              // No PT attribute → PhysicalTenantContext.current() falls back to "default".
              final var request = new MockHttpServletRequest("GET", "/operate/dashboard");
              final var response = new MockHttpServletResponse();
              final var downstream = new MockFilterChain();

              RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
              try {
                proxy.doFilter(request, response, downstream);
              } finally {
                RequestContextHolder.resetRequestAttributes();
              }

              assertThat(response.getStatus()).isEqualTo(200);
              assertThat(downstream.getRequest()).isNotNull(); // request reached downstream
            });
  }

  private WebApplicationContextRunner buildRunner() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(TestBeansConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                CamundaSecurityConfiguration.class,
                BaseSecurityConfiguration.class,
                BasicAuthWebappSecurityConfiguration.class,
                AdminUserCheckFilterConfiguration.class,
                ScopedWebappSecurityChainBuilderConfiguration.class,
                AuthFailureHandlerConfiguration.class))
        .withPropertyValues("camunda.security.authentication.method=basic");
  }

  private static FilterChainProxy buildProxy(final ApplicationContext ctx) {
    final var chains =
        ctx.getBeanProvider(SecurityFilterChain.class).orderedStream().collect(Collectors.toList());
    return new FilterChainProxy(chains);
  }

  static class TestBeansConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    SecurityPathPort securityPathPort() {
      return new SecurityPathAdapter();
    }

    @Bean
    AdminUserPresencePort adminUserPresencePort() {
      return new AdminUserPresenceAdapter(
          DefaultServiceRegistry.of(
              b ->
                  b.roleServices("default", PT_WITH_ADMIN_ROLE_SERVICES)
                      .roleServices(PT_WITH_ADMIN, PT_WITH_ADMIN_ROLE_SERVICES)
                      .roleServices(PT_WITHOUT_ADMIN, PT_WITHOUT_ADMIN_ROLE_SERVICES)),
          new InitializationConfiguration());
    }

    @Bean
    UserDetailsService userDetailsService() {
      return new InMemoryUserDetailsManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
  }
}
