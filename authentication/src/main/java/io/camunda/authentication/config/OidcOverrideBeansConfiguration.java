/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static java.util.stream.Collectors.toMap;

import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.converter.OidcTokenAuthenticationConverter;
import io.camunda.authentication.converter.OidcUserAuthenticationConverter;
import io.camunda.authentication.converter.TokenClaimsConverter;
import io.camunda.authentication.oidc.IssuerAndAudienceAwareOidcDecoderFactory;
import io.camunda.authentication.oidc.MetadataAwareTokenValidatorFactory;
import io.camunda.authentication.pt.PerTenantClientRegistrations;
import io.camunda.authentication.service.MembershipService;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.oidc.CachingOidcClaimsProvider;
import io.camunda.security.oidc.NoopOidcClaimsProvider;
import io.camunda.security.oidc.OidcClaimsProvider;
import io.camunda.security.oidc.OidcUserInfoClient;
import io.camunda.security.spring.handler.OAuth2AuthenticationExceptionHandler;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Host-side OIDC bean overrides. Lifted verbatim from the previous {@code
 * WebSecurityConfig.OidcConfiguration} nested class so the consolidated CSL filter chains pick up
 * OC's bespoke OIDC machinery (multi-IdP, issuer-aware JWT decoding, {@code private_key_jwt},
 * observability instrumentation) instead of the library's defaults — CSL backs off via
 * {@code @ConditionalOnMissingBean} when these beans are present.
 */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class OidcOverrideBeansConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(OidcOverrideBeansConfiguration.class);

  private static final String CAMUNDA_AUTHENTICATION_OBSERVATION_NAME =
      "camunda_authentication_external_requests";
  private static final KeyValues CAMUNDA_AUTHENTICATION_OBSERVATION_DOMAIN_IDENTITY_TAGS =
      KeyValues.of("domain", "identity");

  private static final String PHYSICAL_TENANTS_PREFIX = "camunda.physical-tenants";

  private final SecurityConfiguration securityConfiguration;

  public OidcOverrideBeansConfiguration(final SecurityConfiguration securityConfiguration) {
    this.securityConfiguration = securityConfiguration;
  }

  @PostConstruct
  public void verifyOidcConfiguration() {
    final List<ConfiguredUser> users = securityConfiguration.getInitialization().getUsers();
    if (users != null && !users.isEmpty()) {
      throw new IllegalStateException(
          "Creation of initial users is not supported with `OIDC` authentication method");
    }
  }

  @Bean
  public TokenClaimsConverter tokenClaimsConverter(
      final SecurityConfiguration securityConfiguration,
      final MembershipService membershipService) {
    return new TokenClaimsConverter(securityConfiguration, membershipService);
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> oidcTokenAuthenticationConverter(
      final TokenClaimsConverter tokenClaimsConverter,
      final OidcClaimsProvider oidcClaimsProvider) {
    return new OidcTokenAuthenticationConverter(tokenClaimsConverter, oidcClaimsProvider);
  }

  /**
   * Fallback {@link MeterRegistry} for test / minimal Spring contexts that don't configure the
   * standard Spring Boot auto-configured {@code CompositeMeterRegistry}. In real deployments the
   * app-wide registry wins via {@link ConditionalOnMissingBean} and metrics land on a scraped
   * backend (Prometheus / OTLP / etc.) rather than the in-memory sink.
   */
  @Bean
  @ConditionalOnMissingBean(MeterRegistry.class)
  public MeterRegistry oidcFallbackMeterRegistry() {
    return new SimpleMeterRegistry();
  }

  /**
   * HTTP client used by {@link OidcClaimsProvider} implementations to call the IdP's {@code
   * /userinfo} endpoint. Registered as a Spring-managed bean so its lifecycle is bound to the
   * application context — {@code close()} on shutdown releases the JDK selector + executor threads.
   * Can be overridden by registering a bean of the same name.
   *
   * <p>SSL context is sourced from the {@code oidc-userinfo} entry in {@link SslBundles} when
   * present, so enterprise deployments with custom CA trust under {@code spring.ssl.bundle.*} apply
   * automatically. Falls back to JDK default.
   */
  @Bean(destroyMethod = "close", name = "oidcUserInfoHttpClient")
  @ConditionalOnMissingBean(name = "oidcUserInfoHttpClient")
  public HttpClient oidcUserInfoHttpClient(
      @Autowired(required = false) final SslBundles sslBundles) {
    // Redirects are NOT followed. The JDK HttpClient would re-send the Authorization: Bearer
    // header to the redirect target, which would leak the access token to an attacker-controlled
    // URL if the IdP's /userinfo responded with a 3xx (misconfiguration, open-redirect vuln,
    // hijacked CDN). OIDC Core does not require redirects on /userinfo; any 3xx surfaces as a
    // non-2xx via OidcUserInfoClient.fetch() and degrades to JWT-only claims.
    final HttpClient.Builder builder =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER);
    if (sslBundles != null) {
      try {
        final var bundle = sslBundles.getBundle("oidc-userinfo");
        builder.sslContext(bundle.createSslContext());
        LOG.info(
            "OIDC UserInfo HTTP client using SSL bundle 'oidc-userinfo' "
                + "(custom truststore / trust material)");
      } catch (final NoSuchSslBundleException e) {
        LOG.debug(
            "No 'oidc-userinfo' SSL bundle configured; OIDC UserInfo HTTP client "
                + "uses JDK default SSLContext");
      }
    }
    return builder.build();
  }

  @Bean
  @ConditionalOnMissingBean(OidcClaimsProvider.class)
  public OidcClaimsProvider oidcClaimsProvider(
      final SecurityConfiguration securityConfiguration,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository,
      @Qualifier("oidcUserInfoHttpClient") final HttpClient oidcUserInfoHttpClient,
      final MeterRegistry meterRegistry) {
    final var oidc = securityConfiguration.getAuthentication().getOidc();
    if (oidc == null || !oidc.getUserInfoAugmentation().isEnabled()) {
      return new NoopOidcClaimsProvider();
    }
    // Build a map of issuer -> userinfo URI from the Spring-managed ClientRegistrations.
    // Each ClientRegistration's ProviderDetails.getIssuerUri() is the canonical 'iss' claim
    // the IdP emits on its tokens, and UserInfoEndpoint.getUri() is the userinfo URL from
    // the same discovery document. CachingOidcClaimsProvider uses this to route each
    // token to its own issuer's userinfo endpoint — never cross-wired.
    final Map<String, URI> userInfoUriByIssuer =
        oidcProviderRepository.getOidcAuthenticationConfigurations().keySet().stream()
            .map(clientRegistrationRepository::findByRegistrationId)
            .filter(Objects::nonNull)
            .filter(cr -> cr.getProviderDetails().getIssuerUri() != null)
            .filter(cr -> cr.getProviderDetails().getUserInfoEndpoint().getUri() != null)
            .collect(
                toMap(
                    cr -> cr.getProviderDetails().getIssuerUri(),
                    cr -> URI.create(cr.getProviderDetails().getUserInfoEndpoint().getUri()),
                    (existing, replacement) -> existing));
    if (userInfoUriByIssuer.isEmpty()) {
      throw new IllegalStateException(
          "UserInfo augmentation is enabled but no ClientRegistration exposes a userinfo "
              + "endpoint. Check the IdP's OIDC discovery document and the userInfoEnabled "
              + "flag.");
    }
    return new CachingOidcClaimsProvider(
        oidc,
        userInfoUriByIssuer,
        new OidcUserInfoClient(oidcUserInfoHttpClient, Duration.ofSeconds(2)),
        meterRegistry);
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> oidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory,
      final TokenClaimsConverter tokenClaimsConverter,
      final HttpServletRequest request,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    return new OidcUserAuthenticationConverter(
        authorizedClientRepository,
        oidcAccessTokenDecoderFactory,
        tokenClaimsConverter,
        request,
        buildAdditionalJwkSetUrisByIssuer(oidcProviderRepository),
        buildPreferIdTokenClaimsByRegistrationId(oidcProviderRepository));
  }

  @Bean
  public OidcAuthenticationConfigurationRepository oidcProviderRepository(
      final SecurityConfiguration securityConfiguration) {
    return new OidcAuthenticationConfigurationRepository(securityConfiguration);
  }

  /**
   * OC's per-client {@link OAuth2AuthorizationRequestResolver}. Plugs into the CSL OIDC webapp
   * chain via the SPI hook so multi-IdP redirects, RFC 8707 {@code resource} parameters, and
   * per-provider {@code authorize_request.additional_parameters} are honoured. The CSL chain
   * detects the bean by type and replaces Spring Security's default resolver.
   */
  @Bean
  public OAuth2AuthorizationRequestResolver oauth2AuthorizationRequestResolver(
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    return new ClientAwareOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository, oidcProviderRepository);
  }

  @Bean
  public ClientRegistrationRepository clientRegistrationRepository(
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    final var clientRegistrations =
        oidcProviderRepository.getOidcAuthenticationConfigurations().entrySet().stream()
            .map(e -> createClientRegistration(e.getKey(), e.getValue()))
            .toList();
    return new InMemoryClientRegistrationRepository(clientRegistrations);
  }

  private ClientRegistration createClientRegistration(
      final String registrationId, final OidcConfiguration configuration) {
    try {
      return ClientRegistrationFactory.createClientRegistration(registrationId, configuration);
    } catch (final Exception e) {
      final String issuerUri = configuration.getIssuerUri();
      throw new IllegalStateException(
          "Unable to connect to the Identity Provider endpoint `"
              + issuerUri
              + "'. Double check that it is configured correctly, and if the problem persists, "
              + "contact your external Identity provider.",
          e);
    }
  }

  private List<ClientRegistration> extractClientRegistrations(
      final ClientRegistrationRepository clientRegistrationRepository) {
    if (!(clientRegistrationRepository instanceof final Iterable<?> iterableRepository)) {
      throw new IllegalStateException(
          "Unable to extract OAuth 2.0 client registrations as clientRegistrationRepository %s is not iterable"
              .formatted(clientRegistrationRepository.getClass()));
    }

    return StreamSupport.stream(iterableRepository.spliterator(), false)
        .filter(ClientRegistration.class::isInstance)
        .map(ClientRegistration.class::cast)
        .toList();
  }

  /**
   * Token validator factory that reads each registration's expected audiences from {@link
   * ClientRegistration.ProviderDetails#getConfigurationMetadata()} rather than from a static {@code
   * registrationId -> OidcConfiguration} map keyed off the root {@link SecurityConfiguration}. This
   * is what makes per-PT audience overrides for a SHARED registration id actually take effect — see
   * {@link MetadataAwareTokenValidatorFactory} for the full rationale.
   *
   * <p>The host's stock {@link TokenValidatorFactory} would silently use ROOT audiences for any
   * registration whose id collides between root and a PT (e.g. both root and PT-tenanta declaring
   * the {@code tenanta} registration id with different audiences).
   */
  @Bean
  public TokenValidatorFactory tokenValidatorFactory(
      final SecurityConfiguration securityConfiguration,
      final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
    return new MetadataAwareTokenValidatorFactory(
        securityConfiguration, oidcAuthenticationConfigurationRepository);
  }

  // Gated off under pt-security: the JWS-algorithm lookup map keys by the
  // ClientRegistration object references produced by ClientRegistrationFactory from
  // authentication.providers.oidc.*. The walking-skeleton PT chains construct their
  // own ClientRegistrations inline, so they don't appear in this map and the factory's
  // resolver returns null → missing_signature_verifier on ID-token verification.
  // Under pt-security, Spring Security's default OidcIdTokenDecoderFactory is used.
  @Bean
  @Profile("!pt-security")
  public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
      final TokenValidatorFactory tokenValidatorFactory,
      final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository,
      final ClientRegistrationRepository clientRegistrationRepository) {
    final var decoderFactory = new OidcIdTokenDecoderFactory();
    decoderFactory.setJwtValidatorFactory(tokenValidatorFactory::createTokenValidator);

    final Map<String, OidcConfiguration> oidcAuthenticationConfigurations =
        oidcAuthenticationConfigurationRepository.getOidcAuthenticationConfigurations();
    final Map<ClientRegistration, JwsAlgorithm> clientRegistrationToAlgorithmMap =
        oidcAuthenticationConfigurations.entrySet().stream()
            .collect(
                toMap(
                    e -> clientRegistrationRepository.findByRegistrationId(e.getKey()),
                    e -> parseAlgorithm(e.getValue().getIdTokenAlgorithm())));
    decoderFactory.setJwsAlgorithmResolver(clientRegistrationToAlgorithmMap::get);
    return decoderFactory;
  }

  private SignatureAlgorithm parseAlgorithm(final String algorithm) {
    final SignatureAlgorithm value = SignatureAlgorithm.from(algorithm);
    if (value == null) {
      throw new IllegalStateException("Unsupported signature algorithm: " + algorithm);
    }
    return value;
  }

  @Bean
  public JWSKeySelectorFactory jwsKeySelectorFactory() {
    return new JWSKeySelectorFactory();
  }

  /**
   * Overrides CSL's default {@link OidcAccessTokenDecoderFactory} with a subclass that swaps the
   * issuer-aware validator for {@link
   * io.camunda.authentication.oidc.IssuerAndAudienceAwareTokenValidator}. The custom validator
   * matches the token's {@code iss} against the registered providers; when more than one
   * registration shares the issuer (e.g. two PoC clients on the same Keycloak realm), it
   * disambiguates by intersecting the token's {@code aud} claim with each candidate's declared
   * audiences. Without this override, CSL's {@code IssuerAwareTokenValidator} would always serve
   * the FIRST registration with that issuer, breaking per-provider audience isolation.
   */
  @Bean
  public OidcAccessTokenDecoderFactory accessTokenDecoderFactory(
      final JWSKeySelectorFactory jwsKeySelectorFactory,
      final TokenValidatorFactory tokenValidatorFactory) {
    return new IssuerAndAudienceAwareOidcDecoderFactory(
        jwsKeySelectorFactory, tokenValidatorFactory);
  }

  @Bean
  public JwtDecoder jwtDecoder(
      final OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository,
      // PoC pt-security: optional per-tenant client-registration repositories. Absent under
      // the non-PT setup, present (one entry per physical tenant) under pt-security.
      @Qualifier("ptClientRegistrationRepositories")
          final ObjectProvider<Map<String, ClientRegistrationRepository>>
              ptClientRegistrationRepositories) {
    // Root-side registrations are produced by ClientRegistrationFactory from root config and
    // do NOT carry audiences in their providerConfigurationMetadata (that path doesn't know
    // about the AUDIENCES_METADATA_KEY convention). The metadata-aware validator factory looks
    // up audiences from registration metadata, so we walk each root registration and rebuild it
    // with audiences from the corresponding root OidcConfiguration. PT-side registrations
    // already carry their (possibly-overridden) audiences because PerTenantClientRegistrations
    // stashes them in buildRegistration.
    final var registrations =
        new ArrayList<>(extractClientRegistrations(clientRegistrationRepository))
            .stream()
                .map(reg -> enhanceWithAudiencesMetadata(reg, oidcProviderRepository))
                .collect(Collectors.toCollection(ArrayList::new));
    addPtRegistrations(registrations, ptClientRegistrationRepositories.getIfAvailable());

    final var additionalJwkSetUrisByIssuer =
        buildAdditionalJwkSetUrisByIssuer(oidcProviderRepository);

    if (registrations.size() == 1) {
      final var clientRegistration = registrations.getFirst();
      final var additionalUris =
          additionalJwkSetUrisByIssuer.get(clientRegistration.getProviderDetails().getIssuerUri());
      LOG.info(
          "Create Access Token JWT Decoder for OIDC Provider: {}",
          clientRegistration.getRegistrationId());
      return new SupplierJwtDecoder(
          () ->
              oidcAccessTokenDecoderFactory.createAccessTokenDecoder(
                  clientRegistration, additionalUris));
    } else {
      LOG.info(
          "Create Issuer Aware JWT Decoder for multiple OIDC Providers: [{}]",
          registrations.stream()
              .map(ClientRegistration::getRegistrationId)
              .collect(Collectors.joining(", ")));
      return new SupplierJwtDecoder(
          () ->
              oidcAccessTokenDecoderFactory.createIssuerAwareAccessTokenDecoder(
                  registrations, additionalJwkSetUrisByIssuer));
    }
  }

  /**
   * Merges all {@link ClientRegistration}s from a per-tenant map of {@link
   * ClientRegistrationRepository}s into {@code registrations}.
   *
   * <p>Intentionally does NOT de-duplicate by registration id. Two PTs can register the SAME
   * registration id (e.g. each PT's own view of a shared {@code tenanta} IdP) with different
   * audiences/clientIds — those are logically different registrations because the audience-aware
   * router picks by (iss, aud), not by id. De-duplicating would silently drop a PT's override. The
   * {@code InMemoryClientRegistrationRepository} on each PT chain only ever holds that tenant's own
   * single entry per id, so there's no id collision there; collisions only show up in this
   * cluster-shared validator list, where they're exactly what we want.
   */
  private void addPtRegistrations(
      final List<ClientRegistration> registrations,
      final @Nullable Map<String, ClientRegistrationRepository> ptMap) {
    if (ptMap == null) {
      return;
    }
    for (final var ptRepo : ptMap.values()) {
      registrations.addAll(extractClientRegistrations(ptRepo));
    }
  }

  /**
   * Returns a copy of {@code original} with the matching root {@link OidcConfiguration}'s audiences
   * stashed in {@code providerConfigurationMetadata} under {@link
   * PerTenantClientRegistrations#AUDIENCES_METADATA_KEY}. Returns {@code original} unchanged when
   * the root provider declares no audiences. {@link
   * ClientRegistration#withClientRegistration(ClientRegistration)} preserves the existing metadata
   * map, so any prior entries (e.g. {@code end_session_endpoint} written by {@link
   * ClientRegistrationFactory}) are retained.
   */
  private static ClientRegistration enhanceWithAudiencesMetadata(
      final ClientRegistration original,
      final OidcAuthenticationConfigurationRepository configRepo) {
    final var providerConfig =
        configRepo.getOidcAuthenticationConfigurations().get(original.getRegistrationId());
    if (providerConfig == null
        || providerConfig.getAudiences() == null
        || providerConfig.getAudiences().isEmpty()) {
      return original;
    }
    final Map<String, Object> existingMetadata =
        new HashMap<>(original.getProviderDetails().getConfigurationMetadata());
    existingMetadata.put(
        PerTenantClientRegistrations.AUDIENCES_METADATA_KEY,
        List.copyOf(providerConfig.getAudiences()));
    return ClientRegistration.withClientRegistration(original)
        .providerConfigurationMetadata(existingMetadata)
        .build();
  }

  private Map<String, List<String>> buildAdditionalJwkSetUrisByIssuer(
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    return oidcProviderRepository.getOidcAuthenticationConfigurations().values().stream()
        .filter(
            config ->
                config.getIssuerUri() != null
                    && config.getAdditionalJwkSetUris() != null
                    && !config.getAdditionalJwkSetUris().isEmpty())
        .collect(
            toMap(
                OidcConfiguration::getIssuerUri,
                config -> List.copyOf(config.getAdditionalJwkSetUris()),
                (a, b) -> {
                  if (!a.equals(b)) {
                    throw new IllegalStateException(
                        "Multiple OIDC providers share the same issuer URI with different"
                            + " additional JWKS URIs. Ensure each issuer has a consistent"
                            + " configuration.");
                  }
                  return a;
                }));
  }

  private Map<String, Boolean> buildPreferIdTokenClaimsByRegistrationId(
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    return oidcProviderRepository.getOidcAuthenticationConfigurations().entrySet().stream()
        .filter(entry -> entry.getValue().isPreferIdTokenClaims())
        .collect(toMap(Map.Entry::getKey, entry -> Boolean.TRUE));
  }

  @Bean
  public AssertionJwkProvider assertionJwkProvider(
      final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
    return new AssertionJwkProvider(oidcAuthenticationConfigurationRepository);
  }

  @Bean
  public OidcTokenEndpointCustomizer oidcTokenEndpointCustomizer(
      final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository,
      final AssertionJwkProvider assertionJwkProvider,
      final ObjectProvider<ObservationRegistry> observationRegistry) {
    return new OidcTokenEndpointCustomizer(
        oidcAuthenticationConfigurationRepository,
        assertionJwkProvider,
        restClient(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)));
  }

  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      final ClientRegistrationRepository registrations,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final AssertionJwkProvider assertionJwkProvider,
      final ObjectProvider<ObservationRegistry> observationRegistry) {

    final var manager =
        new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClientRepository);

    // we build a refresh token flow client manually to add support for private_key_jwt
    final var refreshClient = new RestClientRefreshTokenTokenResponseClient();
    refreshClient.setRestClient(
        restClient(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)));
    final var assertionConverter =
        new NimbusJwtClientAuthenticationParametersConverter<OAuth2RefreshTokenGrantRequest>(
            registration -> assertionJwkProvider.createJwk(registration.getRegistrationId()));
    refreshClient.addParametersConverter(assertionConverter);

    final OAuth2AuthorizedClientProvider provider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .authorizationCode()
            .refreshToken(c -> c.accessTokenResponseClient(refreshClient))
            .clientCredentials()
            .build();

    manager.setAuthorizedClientProvider(provider);
    return manager;
  }

  @Bean
  public OidcUserService oidcUserService(
      final ObjectProvider<ObservationRegistry> observationRegistry) {
    final var oauthUserService = new DefaultOAuth2UserService();
    final var oidcUserService = new OidcUserService();
    oidcUserService.setOauth2UserService(oauthUserService);

    // see DefaultOAuth2UserService#setRestOperations for the minimum handlers/converters required
    final var restTemplate = new RestTemplate();
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());

    // instrument user service requests so we can track them via metrics
    restTemplate.setObservationConvention(
        new CustomDefaultClientRequestObservationConvention(
            CAMUNDA_AUTHENTICATION_OBSERVATION_NAME,
            CAMUNDA_AUTHENTICATION_OBSERVATION_DOMAIN_IDENTITY_TAGS));
    restTemplate.setObservationRegistry(
        observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));

    oauthUserService.setRestOperations(restTemplate);
    return oidcUserService;
  }

  @Bean
  @ConditionalOnSecondaryStorageEnabled
  public WebappRedirectStrategy webappRedirectStrategy() {
    return new WebappRedirectStrategy();
  }

  /**
   * OAuth2 login failure handler bean exposed for the library's OIDC webapp chain. Lifted from the
   * inline {@code .oauth2Login(...).failureHandler(new OAuth2AuthenticationExceptionHandler())} in
   * the previous {@code WebSecurityConfig.OidcConfiguration#oidcWebappSecurity}.
   */
  @Bean
  @ConditionalOnSecondaryStorageEnabled
  @ConditionalOnMissingBean(name = "oauth2AuthenticationFailureHandler")
  public AuthenticationFailureHandler oauth2AuthenticationFailureHandler() {
    return new OAuth2AuthenticationExceptionHandler();
  }

  // --------------------------------------------------------------------------------------------
  // Physical-tenant overlays (active only under the pt-security profile).
  //
  // Produces two map beans keyed by tenant id that the jwtDecoder bean above picks up via
  // ObjectProvider:
  //   * ptClientRegistrationRepositories — one ClientRegistrationRepository per tenant,
  //     assembled from camunda.physical-tenants.<id>.security.* + providers.assigned via
  //     PerTenantClientRegistrations#buildFor.
  //   * ptAllowedIssuersPerTenant — the set of OIDC issuer URIs each tenant has assigned;
  //     used both for the per-tenant API chain allowlist and (via the cluster-shared
  //     issuer-aware jwtDecoder) for the unioned validator chain.
  //
  // Tenant ids are enumerated by binding camunda.physical-tenants directly off the Environment
  // — the authentication module does not depend on the configuration module where
  // PhysicalTenantResolver lives.
  // --------------------------------------------------------------------------------------------

  @Bean
  @Profile("pt-security")
  public Map<String, ClientRegistrationRepository> ptClientRegistrationRepositories(
      final Environment environment) {
    final Map<String, ClientRegistrationRepository> repositories = new LinkedHashMap<>();
    for (final String tenantId : readTenantIds(environment)) {
      final SecurityConfiguration tenantSecurity = bindTenantSecurity(tenantId, environment);
      final List<String> assigned = bindAssigned(tenantId, environment);
      repositories.put(
          tenantId,
          PerTenantClientRegistrations.buildFor(
              tenantId, securityConfiguration, tenantSecurity, assigned));
    }
    return Map.copyOf(repositories);
  }

  /**
   * Per-tenant expected-audience allowlist (spec D8 / Task 17). Computed as the union of each
   * assigned OIDC provider's {@link OidcConfiguration#getAudiences()} list — no separate per-tenant
   * config key. Empty when none of the tenant's providers configure {@code audiences}, in which
   * case the per-tenant API chain skips the audience check (back-compat with PT setups whose IdPs
   * don't emit a hardcoded audience claim).
   *
   * <p>This complements (does not replace) {@link #ptAllowedIssuersPerTenant}: the issuer allowlist
   * separates tenants whose IdPs are distinct (different {@code iss}); the audience allowlist
   * separates tenants that share an IdP (same {@code iss}, distinct {@code aud}).
   */
  @Bean
  @Profile("pt-security")
  public Map<String, Set<String>> ptExpectedAudiencesPerTenant(final Environment environment) {
    final Map<String, Set<String>> perTenant = new LinkedHashMap<>();
    for (final String tenantId : readTenantIds(environment)) {
      final SecurityConfiguration tenantSecurity = bindTenantSecurity(tenantId, environment);
      final List<String> assigned = bindAssigned(tenantId, environment);
      if (assigned.isEmpty()) {
        continue;
      }
      final Set<String> audiences = new LinkedHashSet<>();
      for (final String id : assigned) {
        final OidcConfiguration provider =
            resolveProviderForTenant(id, securityConfiguration, tenantSecurity);
        if (provider != null && provider.getAudiences() != null) {
          audiences.addAll(provider.getAudiences());
        }
      }
      perTenant.put(tenantId, Set.copyOf(audiences));
    }
    return Map.copyOf(perTenant);
  }

  @Bean
  @Profile("pt-security")
  public Map<String, Set<String>> ptAllowedIssuersPerTenant(final Environment environment) {
    final Map<String, Set<String>> perTenant = new LinkedHashMap<>();
    for (final String tenantId : readTenantIds(environment)) {
      final SecurityConfiguration tenantSecurity = bindTenantSecurity(tenantId, environment);
      final List<String> assigned = bindAssigned(tenantId, environment);
      if (assigned.isEmpty()) {
        continue;
      }
      final Set<String> issuers = new LinkedHashSet<>();
      for (final String id : assigned) {
        final OidcConfiguration provider =
            resolveProviderForTenant(id, securityConfiguration, tenantSecurity);
        if (provider != null && provider.getIssuerUri() != null) {
          issuers.add(provider.getIssuerUri());
        }
      }
      perTenant.put(tenantId, Set.copyOf(issuers));
    }
    return Map.copyOf(perTenant);
  }

  private static Set<String> readTenantIds(final Environment environment) {
    final Map<String, Object> tenants =
        Binder.get(environment)
            .bind(PHYSICAL_TENANTS_PREFIX, Bindable.mapOf(String.class, Object.class))
            .orElse(Map.of());
    return tenants.keySet();
  }

  private static SecurityConfiguration bindTenantSecurity(
      final String tenantId, final Environment environment) {
    final var tenantSecurity = new SecurityConfiguration();
    Binder.get(environment)
        .bind(
            PHYSICAL_TENANTS_PREFIX + "." + tenantId + ".security",
            Bindable.ofInstance(tenantSecurity));
    return tenantSecurity;
  }

  @SuppressWarnings("unchecked")
  private static List<String> bindAssigned(final String tenantId, final Environment environment) {
    final var bound =
        Binder.get(environment)
            .bind(
                PHYSICAL_TENANTS_PREFIX
                    + "."
                    + tenantId
                    + ".security.authentication.providers.assigned",
                Bindable.listOf(String.class));
    return bound.isBound() ? (List<String>) bound.get() : List.of();
  }

  private static @Nullable OidcConfiguration resolveProviderForTenant(
      final String registrationId,
      final SecurityConfiguration rootSecurity,
      final SecurityConfiguration tenantSecurity) {
    return PerTenantClientRegistrations.resolveMergedProvider(
        registrationId, rootSecurity, tenantSecurity);
  }

  private RestClient restClient(final ObservationRegistry observationRegistry) {
    // The message converters are taken from the expected rest client in
    // AbstractRestClientOAuth2AccessTokenResponseClient
    return RestClient.builder()
        .observationConvention(
            new CustomDefaultClientRequestObservationConvention(
                CAMUNDA_AUTHENTICATION_OBSERVATION_NAME,
                CAMUNDA_AUTHENTICATION_OBSERVATION_DOMAIN_IDENTITY_TAGS))
        .messageConverters(
            (messageConverters) -> {
              messageConverters.clear();
              messageConverters.add(new FormHttpMessageConverter());
              messageConverters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
            })
        .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
        .observationRegistry(observationRegistry)
        .build();
  }
}
