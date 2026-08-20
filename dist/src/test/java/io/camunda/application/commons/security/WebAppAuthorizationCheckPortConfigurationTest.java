/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.AuthorizationService;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.authz.AuthorizationCheckerConfiguration;
import io.camunda.security.spring.authz.AuthorizationConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * Proves that the host-supplied {@link TenantAwareAuthorizationCheckPort} wins the
 * {@code @ConditionalOnMissingBean(AuthorizationCheckPort.class)} race against CSL's default {@link
 * AuthorizationService} bean. Registers the dist configurations via {@code withUserConfiguration}
 * and CSL's competing configurations via {@code AutoConfigurations.of(...)}, mirroring the real
 * ordering: {@code @ComponentScan}-discovered host configurations register before
 * {@code @ImportAutoConfiguration}-imported library configurations.
 */
class WebAppAuthorizationCheckPortConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withBean(
              AuthorizationScopeRepositoryPort.class,
              () -> mock(AuthorizationScopeRepositoryPort.class))
          .withBean(LazyTokenClaimsConverter.class, () -> mock(LazyTokenClaimsConverter.class))
          .withBean(MembershipPort.class, () -> mock(MembershipPort.class))
          .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
          .withConfiguration(
              AutoConfigurations.of(
                  AuthorizationCheckerConfiguration.class, AuthorizationConfiguration.class));

  @Test
  void shouldWireTenantAwarePortWinningConditionalOnMissingBeanRace() {
    // given: the host configurations are registered as user configuration, ahead of CSL's
    // auto-configuration-imported default

    // when / then
    runner
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(AuthorizationCheckPort.class);
              assertThat(ctx.getBean(AuthorizationCheckPort.class))
                  .isInstanceOf(TenantAwareAuthorizationCheckPort.class);
            });
  }

  @Test
  void shouldPerformAuthorizationChecksWithoutLazyTokenClaimsConverterUnderNonOidcAuthentication() {
    // given: no LazyTokenClaimsConverter bean, mirroring BASIC/UNPROTECTED authentication, where
    // OidcOverrideBeansConfiguration (the only producer of this bean) never registers; only the
    // always-available MembershipPort bean is present

    // when / then: the port constructs, and a check() call -- exactly what the webapp
    // authorization filter drives on every authenticated request -- does not throw, since the
    // fallback converter it builds from MembershipPort satisfies AuthorizationService's own
    // non-null requirement even though it is never actually invoked by this code path
    new WebApplicationContextRunner()
        .withBean(
            AuthorizationScopeRepositoryPort.class,
            () -> mock(AuthorizationScopeRepositoryPort.class))
        .withBean(MembershipPort.class, () -> mock(MembershipPort.class))
        .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
        .withConfiguration(
            AutoConfigurations.of(
                AuthorizationCheckerConfiguration.class, AuthorizationConfiguration.class))
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(AuthorizationCheckPort.class);
              final AuthorizationCheckPort port = ctx.getBean(AuthorizationCheckPort.class);
              assertThat(port).isInstanceOf(TenantAwareAuthorizationCheckPort.class);

              final RequiredAuthorization<Void> authorization =
                  RequiredAuthorization.<Void>of(
                          b ->
                              b.resourceType(AuthorizationResourceType.COMPONENT)
                                  .permissionType(PermissionType.ACCESS))
                      .withResourceId("operate");
              assertThat(port.check(CamundaAuthentication.of(b -> b.user("alice")), authorization))
                  .isNotNull();
            });
  }

  @Test
  void shouldPerformAuthorizationChecksWithoutMembershipPortUnderNonOidcAuthentication() {
    // given: neither a MembershipPort nor a LazyTokenClaimsConverter bean, mirroring a bare
    // Zeebe broker/gateway, which satisfies @ConditionalOnAnyHttpGatewayEnabled without ever
    // activating consolidated-auth (the only place io.camunda.authentication's component scan --
    // and therefore both beans -- is registered)

    // when / then: the port still constructs, and a check() call against an already-resolved
    // CamundaAuthentication -- exactly what the webapp authorization filter drives on every
    // authenticated request -- does not throw. This is meaningful because the fallback
    // MembershipPort throws on every method: a passing test here proves check() never invokes it,
    // not merely that it returns something usable.
    new WebApplicationContextRunner()
        .withBean(
            AuthorizationScopeRepositoryPort.class,
            () -> mock(AuthorizationScopeRepositoryPort.class))
        .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
        .withConfiguration(
            AutoConfigurations.of(
                AuthorizationCheckerConfiguration.class, AuthorizationConfiguration.class))
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(AuthorizationCheckPort.class);
              final AuthorizationCheckPort port = ctx.getBean(AuthorizationCheckPort.class);
              assertThat(port).isInstanceOf(TenantAwareAuthorizationCheckPort.class);

              final RequiredAuthorization<Void> authorization =
                  RequiredAuthorization.<Void>of(
                          b ->
                              b.resourceType(AuthorizationResourceType.COMPONENT)
                                  .permissionType(PermissionType.ACCESS))
                      .withResourceId("operate");
              assertThat(port.check(CamundaAuthentication.of(b -> b.user("alice")), authorization))
                  .isNotNull();
            });
  }

  @Test
  void shouldFallBackToDefaultAuthorizationServiceWhenHostPortAbsent() {
    // given: no host override registered, only CSL's own configurations

    // when / then: CSL's default AuthorizationCheckPort backs off to nothing else, so its own
    // AuthorizationService is exactly the bean that would otherwise ship the default behavior --
    // confirming the win in the other test comes from bean ordering, not from CSL's bean being
    // uncreatable
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(AuthorizationCheckPort.class);
          assertThat(ctx.getBean(AuthorizationCheckPort.class))
              .isInstanceOf(AuthorizationService.class);
        });
  }

  @Test
  void shouldFailHardWhenPerTenantScopesConfiguredButNoPhysicalTenantResolved() {
    // given: a PhysicalTenantSearchClientReaders bean is present, so this bean wires one scope per
    // physical tenant instead of the single "default" entry -- and this test runs outside any
    // request scope or propagated tenant, so PhysicalTenantContext.currentOrNull() returns null
    final var readersStub = mock(SearchClientReaders.class);
    final var perTenantReaders =
        new PhysicalTenantSearchClientReaders(Map.of("tenant-a", readersStub));

    // when / then: CSL's fail-hard forScope(null) surfaces rather than silently resolving against
    // tenant-a's storage, proving this bean method actually wires the per-tenant branch (not just
    // the single-default-entry one every other test in this class exercises)
    new WebApplicationContextRunner()
        .withBean(
            AuthorizationScopeRepositoryPort.class,
            () -> mock(AuthorizationScopeRepositoryPort.class))
        .withBean(PhysicalTenantSearchClientReaders.class, () -> perTenantReaders)
        .withBean(MembershipPort.class, () -> mock(MembershipPort.class))
        .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
        .withConfiguration(
            AutoConfigurations.of(
                AuthorizationCheckerConfiguration.class, AuthorizationConfiguration.class))
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(AuthorizationCheckPort.class);
              final AuthorizationCheckPort port = ctx.getBean(AuthorizationCheckPort.class);

              final RequiredAuthorization<Void> authorization =
                  RequiredAuthorization.<Void>of(
                          b ->
                              b.resourceType(AuthorizationResourceType.COMPONENT)
                                  .permissionType(PermissionType.ACCESS))
                      .withResourceId("operate");
              assertThatIllegalStateException()
                  .isThrownBy(
                      () ->
                          port.check(
                              CamundaAuthentication.of(b -> b.user("alice")), authorization));
            });
  }

  @Test
  void shouldFailFastAtStartupWhenPerTenantScopesReportedButNoTenantsDeclared() {
    // given: a PhysicalTenantSearchClientReaders bean is present but backed by an empty map --
    // simulating a broken PhysicalTenantResolver invariant (it is expected to always synthesize a
    // "default" entry, see PhysicalTenantResolverTest), which this bean has no way to verify itself
    final var emptyReaders = new PhysicalTenantSearchClientReaders(Map.of());

    // when / then: construction fails loudly at startup instead of leaving every authorization
    // check -- including default-tenant ones -- to fail hard on every subsequent request
    new WebApplicationContextRunner()
        .withBean(
            AuthorizationScopeRepositoryPort.class,
            () -> mock(AuthorizationScopeRepositoryPort.class))
        .withBean(PhysicalTenantSearchClientReaders.class, () -> emptyReaders)
        .withBean(MembershipPort.class, () -> mock(MembershipPort.class))
        .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
        .withConfiguration(
            AutoConfigurations.of(
                AuthorizationCheckerConfiguration.class, AuthorizationConfiguration.class))
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "PhysicalTenantSearchClientReaders is present but declares no physical "
                          + "tenants; expected PhysicalTenantResolver to always synthesize a "
                          + "'default' entry. This indicates a broken invariant between "
                          + "PhysicalTenantResolver and this bean -- every authorization check, "
                          + "including default-tenant ones, would otherwise fail hard per request "
                          + "instead of at startup.");
            });
  }
}
