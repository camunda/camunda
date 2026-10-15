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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import io.camunda.security.api.model.authz.AuthorizationRejection;
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
import io.camunda.security.spring.spi.WebAppAuthorizationEnabledPort;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Proves that the host-supplied {@link TenantAwareAuthorizationCheckPort} wins the
 * {@code @ConditionalOnMissingBean(AuthorizationCheckPort.class)} race against CSL's default {@link
 * AuthorizationService} bean. Registers the dist configurations via {@code withUserConfiguration}
 * and CSL's competing configurations via {@code AutoConfigurations.of(...)}, mirroring the real
 * ordering: {@code @ComponentScan}-discovered host configurations register before
 * {@code @ImportAutoConfiguration}-imported library configurations.
 */
class WebAppAuthorizationCheckPortConfigurationTest {

  private static final PhysicalTenantSecurityProperties DEFAULT_SECURITY_PROPERTIES =
      new PhysicalTenantSecurityProperties(
          Map.of(
              "default", new CamundaSecurityLibraryProperties(),
              "tenant-a", new CamundaSecurityLibraryProperties()));

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
        .withBean(PhysicalTenantSecurityProperties.class, () -> DEFAULT_SECURITY_PROPERTIES)
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
        .withBean(PhysicalTenantSecurityProperties.class, () -> DEFAULT_SECURITY_PROPERTIES)
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
        .withBean(PhysicalTenantSecurityProperties.class, () -> DEFAULT_SECURITY_PROPERTIES)
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
        .withBean(PhysicalTenantSecurityProperties.class, () -> DEFAULT_SECURITY_PROPERTIES)
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

  @ParameterizedTest(name = "root authorizations enabled: {0}")
  @ValueSource(booleans = {true, false})
  void shouldApplyEachPhysicalTenantsOwnAuthorizationsFlag(
      final boolean rootAuthorizationsEnabled) {
    // given: tenanta on, tenantb off, whatever the root value; nobody holds any grant
    final var perTenantReaders =
        new PhysicalTenantSearchClientReaders(
            Map.of("tenanta", readersWithoutGrants(), "tenantb", readersWithoutGrants()));
    final var securityProperties =
        new PhysicalTenantSecurityProperties(
            Map.of(
                "tenanta", securityProperties(true),
                "tenantb", securityProperties(false)));

    new WebApplicationContextRunner()
        .withBean(
            AuthorizationScopeRepositoryPort.class,
            () -> mock(AuthorizationScopeRepositoryPort.class))
        .withBean(PhysicalTenantSearchClientReaders.class, () -> perTenantReaders)
        .withBean(PhysicalTenantSecurityProperties.class, () -> securityProperties)
        .withBean(MembershipPort.class, () -> mock(MembershipPort.class))
        .withBean(
            CamundaSecurityLibraryProperties.class,
            () -> securityProperties(rootAuthorizationsEnabled))
        .withConfiguration(
            AutoConfigurations.of(
                AuthorizationCheckerConfiguration.class, AuthorizationConfiguration.class))
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              final AuthorizationCheckPort port = ctx.getBean(AuthorizationCheckPort.class);

              // when
              final var tenantaResult = checkOperateAccessInPhysicalTenant(port, "tenanta");
              final var tenantbResult = checkOperateAccessInPhysicalTenant(port, "tenantb");

              // then
              assertThat(tenantaResult.isLeft()).isTrue();
              assertThat(tenantbResult.isRight()).isTrue();
            });
  }

  @Test
  void shouldResolveWebAppAuthorizationFlagFromRequestPhysicalTenant() {
    // given: the default tenant has authorizations off, tenanta on, tenantb off
    final var securityProperties =
        new PhysicalTenantSecurityProperties(
            Map.of(
                "default", securityProperties(false),
                "tenanta", securityProperties(true),
                "tenantb", securityProperties(false)));

    runner
        .withBean(PhysicalTenantSecurityProperties.class, () -> securityProperties)
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              final var enabledPort = ctx.getBean(WebAppAuthorizationEnabledPort.class);

              // when / then
              assertThat(enabledPort.isAuthorizationEnabled(requestInPhysicalTenant("tenanta")))
                  .isTrue();
              assertThat(enabledPort.isAuthorizationEnabled(requestInPhysicalTenant("tenantb")))
                  .isFalse();
              assertThat(enabledPort.isAuthorizationEnabled(new MockHttpServletRequest()))
                  .isFalse();
            });
  }

  @Test
  void shouldApplyDefaultPhysicalTenantFlagWithoutSecondaryStorage() {
    // given: root authorizations on, the default tenant off, no per-tenant secondary storage;
    // nobody holds any grant
    final var securityProperties =
        new PhysicalTenantSecurityProperties(Map.of("default", securityProperties(false)));

    runner
        .withBean(PhysicalTenantSecurityProperties.class, () -> securityProperties)
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              // when
              final var result =
                  checkOperateAccessInPhysicalTenant(
                      ctx.getBean(AuthorizationCheckPort.class), "default");

              // then
              assertThat(result.isRight()).isTrue();
            });
  }

  @Test
  void shouldFailFastAtStartupWhenPhysicalTenantHasNoSecurityProperties() {
    // given: readers exist for a physical tenant that has no security properties
    final var readers =
        new PhysicalTenantSearchClientReaders(Map.of("tenant-missing", readersWithoutGrants()));

    runner
        .withBean(PhysicalTenantSearchClientReaders.class, () -> readers)
        .withBean(PhysicalTenantSecurityProperties.class, () -> DEFAULT_SECURITY_PROPERTIES)
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              // then
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "No security properties for physical tenant 'tenant-missing'");
            });
  }

  @Test
  void shouldTreatWebAppAuthorizationAsEnabledForUnknownPhysicalTenant() {
    // given: every known tenant has authorizations off
    final var securityProperties =
        new PhysicalTenantSecurityProperties(Map.of("default", securityProperties(false)));

    runner
        .withBean(PhysicalTenantSecurityProperties.class, () -> securityProperties)
        .withUserConfiguration(WebAppAuthorizationCheckPortConfiguration.class)
        .run(
            ctx -> {
              final var enabledPort = ctx.getBean(WebAppAuthorizationEnabledPort.class);

              // when / then: the request reaches the check port, which fails hard for the tenant
              assertThat(
                      enabledPort.isAuthorizationEnabled(requestInPhysicalTenant("tenant-unknown")))
                  .isTrue();
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
        .withBean(PhysicalTenantSecurityProperties.class, () -> DEFAULT_SECURITY_PROPERTIES)
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

  private static SearchClientReaders readersWithoutGrants() {
    final var authorizationReader = mock(AuthorizationReader.class);
    when(authorizationReader.search(any(), any())).thenReturn(SearchQueryResult.empty());
    final var readers = mock(SearchClientReaders.class);
    when(readers.authorizationReader()).thenReturn(authorizationReader);
    return readers;
  }

  private static CamundaSecurityLibraryProperties securityProperties(
      final boolean authorizationsEnabled) {
    final var properties = new CamundaSecurityLibraryProperties();
    properties.getAuthorizations().setEnabled(authorizationsEnabled);
    return properties;
  }

  private static MockHttpServletRequest requestInPhysicalTenant(final String physicalTenantId) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, physicalTenantId);
    return request;
  }

  private static Either<AuthorizationRejection, Void> checkOperateAccessInPhysicalTenant(
      final AuthorizationCheckPort port, final String physicalTenantId) {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(requestInPhysicalTenant(physicalTenantId)));
    try {
      return port.check(
          CamundaAuthentication.of(b -> b.user("alice")),
          RequiredAuthorization.<Void>of(
                  b ->
                      b.resourceType(AuthorizationResourceType.COMPONENT)
                          .permissionType(PermissionType.ACCESS))
              .withResourceId("operate"));
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }
}
