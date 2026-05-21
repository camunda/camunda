/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.authentication.pt.TenantSecuritySlice.AccessPath;
import io.camunda.authentication.session.WebSession;
import io.camunda.authentication.session.WebSessionRepository;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Programmatically registers one {@link SecurityFilterChain} bean per (physical tenant × variant)
 * combination, replacing the previous handful of explicit {@code @Bean} methods on {@code
 * PhysicalTenantSecurityConfiguration}.
 *
 * <p>Tenant ids are enumerated directly from the environment under {@code
 * camunda.physical-tenants.*} via {@link Binder} — the same pattern used by {@code
 * OidcOverrideBeansConfiguration#readTenantIds} — so the authentication module avoids a
 * back-dependency on the configuration module where {@code PhysicalTenantResolver} lives.
 *
 * <p>For each tenant the registrar emits:
 *
 * <ol>
 *   <li>{@code pt<TenantPascal>ApiChain} — the prefixed API chain (session-or-bearer at {@code
 *       /physical-tenant/<id>/v2/**} and {@code /v2/physical-tenants/<id>/**}).
 *   <li>{@code pt<TenantPascal>WebappChain} — the prefixed webapp/OAuth2 login chain at {@code
 *       /physical-tenant/<id>/**}.
 * </ol>
 *
 * <h2>Ordering</h2>
 *
 * Spring Security's chain collector sorts {@link SecurityFilterChain} beans by {@link
 * org.springframework.core.annotation.AnnotationAwareOrderComparator}. With programmatically
 * registered {@link RootBeanDefinition}s there is no annotated factory method to read
 * {@code @Order} from, so we attach precedence by wrapping each chain in an {@link
 * OrderedSecurityFilterChain} instance — the comparator honours {@link
 * org.springframework.core.Ordered} on the bean instance itself.
 *
 * <p>The PT chains must interleave with CSL's {@code BaseSecurityConfiguration} chains, which sit
 * at {@code @Order(0)} (unprotected paths), {@code @Order(1)} (OidcWebapp / OidcApi / BasicAuth),
 * and {@code @Order(2)} (catch-all 404). Concretely:
 *
 * <ul>
 *   <li>PT API chains use {@code @Order(-1)}. They MUST beat CSL's {@code OidcApi} at
 *       {@code @Order(1)} because PT API matchers like {@code /v2/physical-tenants/<id>/**} are
 *       sub-patterns of CSL's {@code /v2/**} matcher. Among themselves their matchers are
 *       per-tenant disjoint.
 *   <li>PT webapp chains use {@code @Order(0)}. They tie with CSL's unprotected-paths chain, but
 *       the matchers are disjoint: {@code SecurityPathPort.unprotectedPaths()} does not include any
 *       PT or {@code /v2/...} URL. The PT API chains are at a strictly lower order so URLs like
 *       {@code /physical-tenant/<id>/v2/whoami} hit the API chain before the webapp chain.
 * </ul>
 *
 * Final ordering at runtime (low number wins):
 *
 * <pre>
 *  -1  PT API chains (prefixed)
 *   0  PT webapp chains (prefixed)
 *   0  CSL unprotectedPathsSecurityFilterChain (disjoint matchers — favicon, /error, /actuator, ...)
 *   1  CSL OidcWebapp + OidcApi (serve the unprefixed default tenant via /v2/** and webapp paths)
 *   2  CSL protectedUnhandledPathsSecurityFilterChain (/** denyAll -> 404, the catch-all)
 * </pre>
 *
 * <p>With N tenants the result is 2N chains (one API + one webapp per tenant). The unprefixed
 * default access path is served by CSL's standard {@code OidcWebapp} + {@code OidcApi} chains, not
 * by PoC-owned chains. Adding a tenant to {@code application-pt-poc.yaml} automatically grows the
 * chain set; no Java changes required.
 */
@NullMarked
public final class PhysicalTenantSecurityChainRegistrar
    implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

  static final String PHYSICAL_TENANTS_PREFIX = "camunda.physical-tenants";
  static final String DEFAULT_TENANT_ID = "default";

  private @Nullable Environment environment;

  @Override
  public void setEnvironment(final Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry) {
    final Environment env = environment;
    if (env == null) {
      return;
    }
    // Self-gate on config: no tenants ⇒ no PT chains registered. The host then boots as a
    // single-tenant deployment served by CSL's standard chains. Adding/removing entries under
    // camunda.physical-tenants.* is the only switch — there is no separate profile to flip.
    final var tenantIds = readTenantIds(env);
    if (tenantIds.isEmpty()) {
      return;
    }

    // Ordering is split into two groups so the PT chains interleave correctly with CSL's
    // BaseSecurityConfiguration chains — see class javadoc. API chains run ahead of CSL's
    // OidcApi (@Order(1)); webapp chains tie with CSL's unprotected-paths chain at @Order(0)
    // (disjoint matchers).
    final int apiOrder = -1;
    final int webappOrder = 0;

    // 1) Prefixed API chains — one per tenant, ordered by config iteration order.
    for (final String tenantId : tenantIds) {
      registerChain(
          registry, beanName(tenantId, "ApiChain"), apiOrder, ChainVariant.PREFIXED_API, tenantId);
    }
    // 2) Prefixed webapp chains — one per tenant.
    for (final String tenantId : tenantIds) {
      registerChain(
          registry,
          beanName(tenantId, "WebappChain"),
          webappOrder,
          ChainVariant.PREFIXED_WEBAPP,
          tenantId);
    }
  }

  private static void registerChain(
      final BeanDefinitionRegistry registry,
      final String beanName,
      final int order,
      final ChainVariant variant,
      final String tenantId) {
    final RootBeanDefinition def = new RootBeanDefinition(OrderedSecurityFilterChain.class);
    def.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    def.setInstanceSupplier(() -> buildChain(registry, variant, tenantId, order));
    registry.registerBeanDefinition(beanName, def);
  }

  private static OrderedSecurityFilterChain buildChain(
      final BeanDefinitionRegistry registry,
      final ChainVariant variant,
      final String tenantId,
      final int order) {
    final BeanFactory beanFactory = (BeanFactory) registry;
    final PerTenantSecurityChainFactory factory = new PerTenantSecurityChainFactory();
    final HttpSecurity http = beanFactory.getBean(HttpSecurity.class);
    try {
      final SecurityFilterChain chain =
          switch (variant) {
            case PREFIXED_API ->
                factory.buildApiChain(
                    http,
                    sliceForPrefixed(beanFactory, tenantId),
                    beanFactory.getBean(JwtDecoder.class),
                    allowedIssuersFor(beanFactory, tenantId),
                    expectedAudiencesFor(beanFactory, tenantId));
            case PREFIXED_WEBAPP ->
                factory.buildWebappChain(http, sliceForPrefixed(beanFactory, tenantId));
          };
      return new OrderedSecurityFilterChain(chain, order);
    } catch (final Exception e) {
      throw new IllegalStateException(
          "Failed to build PT SecurityFilterChain '" + variant + "' for tenant '" + tenantId + "'",
          e);
    }
  }

  // ----- slice + lookup helpers ---------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static TenantSecuritySlice sliceForPrefixed(
      final BeanFactory beanFactory, final String tenantId) {
    final var repos =
        (Map<String, ClientRegistrationRepository>)
            beanFactory.getBean("ptClientRegistrationRepositories", Map.class);
    final ClientRegistrationRepository repo = repos.get(tenantId);
    if (repo == null) {
      throw new IllegalStateException(
          "No ClientRegistrationRepository bean for physical tenant '" + tenantId + "'");
    }
    final var serializer = PhysicalTenantCookieSerializer.forPrefixedChain(tenantId);
    final CookieHttpSessionIdResolver resolver =
        PhysicalTenantCookieSerializer.resolver(serializer);
    final SessionRepositoryFilter<WebSession> sessionFilter =
        new SessionRepositoryFilter<>(webSessionRepositoryFor(beanFactory, tenantId));
    sessionFilter.setHttpSessionIdResolver(resolver);
    return new TenantSecuritySlice(tenantId, AccessPath.PREFIXED, repo, sessionFilter, resolver);
  }

  @SuppressWarnings("unchecked")
  private static WebSessionRepository webSessionRepositoryFor(
      final BeanFactory beanFactory, final String tenantId) {
    final var repos =
        (Map<String, WebSessionRepository>)
            beanFactory.getBean("ptWebSessionRepositories", Map.class);
    final WebSessionRepository repository = repos.get(tenantId);
    if (repository == null) {
      throw new IllegalStateException(
          "No WebSessionRepository bean for physical tenant '" + tenantId + "'");
    }
    return repository;
  }

  @SuppressWarnings("unchecked")
  private static Set<String> allowedIssuersFor(
      final BeanFactory beanFactory, final String tenantId) {
    final var perTenant =
        (Map<String, Set<String>>) beanFactory.getBean("ptAllowedIssuersPerTenant", Map.class);
    final Set<String> issuers = perTenant.get(tenantId);
    if (issuers == null) {
      throw new IllegalStateException(
          "No allowed-issuers entry for physical tenant '" + tenantId + "'");
    }
    return issuers;
  }

  /**
   * Per-tenant expected-audience allowlist (spec D8). Absent or empty entry means "skip the
   * audience check on this tenant's API chain" (back-compat with pre-Task-17 setups).
   */
  @SuppressWarnings("unchecked")
  private static Set<String> expectedAudiencesFor(
      final BeanFactory beanFactory, final String tenantId) {
    final var perTenant =
        (Map<String, Set<String>>) beanFactory.getBean("ptExpectedAudiencesPerTenant", Map.class);
    final Set<String> audiences = perTenant.get(tenantId);
    return audiences == null ? Set.of() : audiences;
  }

  // ----- environment + naming -----------------------------------------------------------------

  static Set<String> readTenantIds(final Environment environment) {
    final Map<String, Object> tenants =
        Binder.get(environment)
            .bind(PHYSICAL_TENANTS_PREFIX, Bindable.mapOf(String.class, Object.class))
            .orElse(new LinkedHashMap<>());
    return tenants.keySet();
  }

  static String beanName(final String tenantId, final String suffix) {
    // pascal-case the tenant id so the bean name reads naturally (tenanta -> Tenanta, default ->
    // Default).
    if (tenantId.isEmpty()) {
      return "pt" + suffix;
    }
    final String pascal = tenantId.substring(0, 1).toUpperCase(Locale.ROOT) + tenantId.substring(1);
    // Preserve the historical name "ptDefaultPrefixedApiChain"/"ptDefaultPrefixedWebappChain"
    // for the default tenant's prefixed chains so anything looking these up by name keeps working.
    if (DEFAULT_TENANT_ID.equals(tenantId)) {
      return "pt" + pascal + "Prefixed" + suffix;
    }
    return "pt" + pascal + suffix;
  }

  private enum ChainVariant {
    PREFIXED_API,
    PREFIXED_WEBAPP
  }
}
