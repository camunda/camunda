/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.authentication.config.BasicAuthBeansConfiguration;
import io.camunda.authentication.config.OidcOverrideBeansConfiguration;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.CamundaAuthenticationHolder;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.in.ResourcePermissionPort;
import io.camunda.security.core.port.out.AuthorizationRepositoryPort;
import io.camunda.security.core.reader.ResourceAccessProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.context.holder.HttpSessionBasedAuthenticationHolder;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.service.registry.ServiceRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ForkJoinPool;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Slice-test bootstrap. Mirrors the previous {@code WebSecurityConfigTestContext} by importing the
 * new host security configuration plus its OIDC/basic-auth bean providers, and supplying the
 * supporting beans tests need (services, authentication providers, resource access provider, etc.)
 * without dragging in the search layer.
 */
@SpringBootConfiguration
@Import({
  WebSecurityConfig.class,
  OidcOverrideBeansConfiguration.class,
  BasicAuthBeansConfiguration.class
})
@EnableConfigurationProperties(CamundaSecurityLibraryProperties.class)
public class WebSecurityConfigTestContext {

  @Bean
  @RequestScope
  public CamundaAuthenticationHolder httpSessionBasedAuthenticationHolder(
      final HttpServletRequest request, final CamundaSecurityLibraryProperties properties) {
    return new HttpSessionBasedAuthenticationHolder(request, properties.getAuthentication());
  }

  /**
   * @return REST controller with dummy endpoints for testing
   */
  @Bean
  public TestApiController createTestController(
      final CamundaAuthenticationProvider camundaAuthenticationProvider) {
    return new TestApiController(camundaAuthenticationProvider);
  }

  @Bean
  public UserDetailsService createUserDetailsService() {
    return new TestUserDetailsService();
  }

  @Bean
  public ApiServicesExecutorProvider apiServicesExecutorProvider() {
    return new ApiServicesExecutorProvider(ForkJoinPool.commonPool());
  }

  @Bean
  public RoleServices createRoleServices(final ApiServicesExecutorProvider executorProvider) {
    return new RoleServices(null, null, null, executorProvider, null);
  }

  @Bean
  public GroupServices createGroupServices(final ApiServicesExecutorProvider executorProvider) {
    return new GroupServices(null, null, null, executorProvider, null);
  }

  @Bean
  public TenantServices createTenantServices(final ApiServicesExecutorProvider executorProvider) {
    return new TenantServices(null, null, null, executorProvider, null);
  }

  @Bean
  public ServiceRegistry serviceRegistry(
      final RoleServices roleServices,
      final GroupServices groupServices,
      final TenantServices tenantServices) {
    return DefaultServiceRegistry.of(
        b ->
            b.roleServices("default", roleServices)
                .groupServices("default", groupServices)
                .tenantServices("default", tenantServices));
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> testAuthenticationConverter() {
    return new CamundaAuthenticationConverter() {
      @Override
      public boolean supports(final Object authentication) {
        return authentication instanceof TestingAuthenticationToken;
      }

      @Override
      public CamundaAuthentication convert(final Object authentication) {
        return (CamundaAuthentication) ((TestingAuthenticationToken) authentication).getPrincipal();
      }
    };
  }

  @Bean
  public ResourceAccessProvider createResourceAccessProvider() {
    return new DisabledResourceAccessProvider();
  }

  /**
   * Empty {@link AuthorizationRepositoryPort} so {@link WebSecurityConfig} wires the {@code
   * WebAppAuthorizationCheckFilter} into the webapp chain (its own filter bean is gated on {@link
   * ResourcePermissionPort}, which the host config builds from this repository port). Tests rely on
   * the filter calling {@link CamundaAuthenticationProvider#getCamundaAuthentication()} so the
   * host's {@code HttpSessionBasedAuthenticationHolder} initialises the session-level refresh
   * attribute.
   */
  @Bean
  public AuthorizationRepositoryPort createAuthorizationRepositoryPort() {
    return (authentication, resourceType) -> java.util.Set.of();
  }

  /**
   * Test-scoped {@link ResourcePermissionPort} that answers {@code true} unconditionally so the
   * filter passes through after {@link CamundaAuthenticationProvider#getCamundaAuthentication()}
   * has fired the {@code HttpSessionBasedAuthenticationHolder.set(...)} call that initialises the
   * session-level refresh attribute the {@code SessionAuthenticationRefreshTest} suite asserts on.
   * Overrides the host {@code ResourcePermissionService} (which is annotated
   * {@code @ConditionalOnMissingBean(ResourcePermissionPort.class)}). Tests that need a real
   * authorization decision should override this bean.
   */
  @Bean
  public ResourcePermissionPort resourcePermissionPort() {
    return (authentication, resourceType, resourceId, permissionType) -> true;
  }

  /**
   * @return plain-text password encoder so that conversely we can use plain-text passwords in
   *     TestUserDetailsService
   */
  @Bean
  public PasswordEncoder createPasswordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }

  // CSL's default JsonProblemDetailAuthFailureHandler requires an ObjectMapper; slice tests don't
  // pull JacksonAutoConfiguration so we provide one explicitly.
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  /**
   * Replacing CustomMethodSecurityExpressionHandler which requires the search layer and is only
   * there to support hasPermission annotations on the old V1 APIs
   */
  @Bean
  public MethodSecurityExpressionHandler createMethodSecurityExpressionHandler() {
    return new DefaultMethodSecurityExpressionHandler();
  }
}
