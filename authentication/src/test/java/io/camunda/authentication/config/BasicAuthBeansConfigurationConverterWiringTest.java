/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.authentication.service.PhysicalTenantMembershipContextPropagator;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.context.CamundaAuthenticationBeansConfiguration;
import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Checks that BASIC-auth login wraps its lazy membership lists with the host's propagator, so the
 * lists still work after the request that created them has ended.
 */
@ExtendWith(MockitoExtension.class)
class BasicAuthBeansConfigurationConverterWiringTest {

  @Mock private MembershipPort membershipPort;

  @Test
  void shouldSupplyLibraryPropagatorWithoutThisConfiguration() {
    // given only the library's propagator bean
    // when the container is asked for a propagator
    // then it returns the library's, not ours — proof the library defines one at all, without which
    // the next test would prove nothing
    libraryContext()
        .run(
            context ->
                assertThat(context.getBean(MembershipResolutionContextPropagator.class))
                    .isNotInstanceOf(PhysicalTenantMembershipContextPropagator.class));
  }

  @Test
  void shouldResolvePhysicalTenantPropagatorRatherThanTheLibraryDefault() {
    // given the library's propagator bean
    // when this configuration is added the same way WebSecurityConfig adds it
    // then Spring picks ours and the library's steps aside
    libraryContext()
        .withUserConfiguration(MembershipResolutionContextPropagatorConfiguration.class)
        .run(
            context ->
                assertThat(context.getBean(MembershipResolutionContextPropagator.class))
                    .isInstanceOf(PhysicalTenantMembershipContextPropagator.class));
  }

  @Test
  void shouldResolveMembershipAgainstTenantCapturedAtLoginAfterRequestScopeEnds() {
    // given a login that carries a physical tenant, converted with the real propagator
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, "tenant-a");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    final Map<String, String> tenantSeenByLookup = new HashMap<>();
    when(membershipPort.groupIds(any()))
        .thenAnswer(
            invocation -> {
              tenantSeenByLookup.put("groupIds", PhysicalTenantContext.current());
              return List.of("group-1");
            });
    when(membershipPort.roleIds(any()))
        .thenAnswer(
            invocation -> {
              tenantSeenByLookup.put("roleIds", PhysicalTenantContext.current());
              return List.of("role-1");
            });
    when(membershipPort.tenantIds(any()))
        .thenAnswer(
            invocation -> {
              tenantSeenByLookup.put("tenantIds", PhysicalTenantContext.current());
              return List.of("tenant-1");
            });

    final var configuration = configuration();
    final var authentication =
        configuration
            .usernamePasswordAuthenticationConverter(
                membershipPort, new PhysicalTenantMembershipContextPropagator())
            .convert(new UsernamePasswordAuthenticationToken("alice", "pw"));

    // when the request has ended before any list is read, as happens when a stored session writes
    // the authentication out
    RequestContextHolder.resetRequestAttributes();

    // then every lookup uses the tenant from login time. Without the wrapping it would throw here.
    assertThat(authentication.authenticatedGroupIds()).containsExactly("group-1");
    assertThat(authentication.authenticatedRoleIds()).containsExactly("role-1");
    assertThat(authentication.authenticatedTenantIds()).containsExactly("tenant-1");
    assertThat(tenantSeenByLookup)
        .containsEntry("groupIds", "tenant-a")
        .containsEntry("roleIds", "tenant-a")
        .containsEntry("tenantIds", "tenant-a");
  }

  @AfterEach
  void clearRequestScope() {
    // if a test fails early, the request it bound would leak into the next test
    RequestContextHolder.resetRequestAttributes();
  }

  private static BasicAuthBeansConfiguration configuration() {
    return new BasicAuthBeansConfiguration(new CamundaSecurityLibraryProperties());
  }

  /**
   * A BASIC-auth context with the library configuration that defines the competing propagator bean.
   * The beans added here are only what it needs to start up; none of them affect the assertions.
   */
  private ApplicationContextRunner libraryContext() {
    return new ApplicationContextRunner()
        .withPropertyValues("camunda.security.authentication.method=basic")
        .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
        .withBean(MembershipPort.class, () -> membershipPort)
        .withBean(HttpServletRequest.class, MockHttpServletRequest::new)
        .withConfiguration(AutoConfigurations.of(CamundaAuthenticationBeansConfiguration.class));
  }
}
