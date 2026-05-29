/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static java.util.stream.Collectors.toMap;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.oidc.CachingOidcClaimsProvider;
import io.camunda.security.oidc.NoopOidcClaimsProvider;
import io.camunda.security.oidc.OidcClaimsProvider;
import io.camunda.security.oidc.OidcUserInfoClient;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.annotation.ConditionalOnAuthenticationMethod;
import io.camunda.security.spring.converter.LazyTokenClaimsConverter;
import io.camunda.security.spring.converter.OidcTokenAuthenticationConverter;
import io.camunda.security.spring.converter.OidcUserAuthenticationConverter;
import io.camunda.security.spring.handler.OAuth2AuthenticationExceptionHandler;
import io.camunda.security.spring.oidc.AssertionJwkProvider;
import io.camunda.security.spring.oidc.JWSKeySelectorFactory;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
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

  private final CamundaSecurityLibraryProperties cslProperties;

  public OidcOverrideBeansConfiguration(final CamundaSecurityLibraryProperties cslProperties) {
    this.cslProperties = cslProperties;
  }

  @PostConstruct
  public void verifyOidcConfiguration() {
    final List<ConfiguredUser> users = cslProperties.getInitialization().getUsers();
    if (users != null && !users.isEmpty()) {
      throw new IllegalStateException(
          "Creation of initial users is not supported with `OIDC` authentication method");
    }
  }

  @Bean
  public LazyTokenClaimsConverter tokenClaimsConverter(
      final CamundaSecurityLibraryProperties cslProperties, final MembershipPort membershipPort) {
    return new LazyTokenClaimsConverter(
        cslProperties.getAuthentication().getOidc(), membershipPort);
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> oidcTokenAuthenticationConverter(
      final LazyTokenClaimsConverter tokenClaimsConverter,
      final OidcClaimsProvider hostClaimsProvider) {
    // Bridge host's local OidcClaimsProvider (io.camunda.security.oidc.*) to CSL's
    // (io.camunda.security.api.context.*). Interfaces have identical shape; this small
    // adapter avoids touching the broker/gateway codepaths that still depend on the host
    // interface — tracked as a follow-up in https://github.com/camunda/camunda/issues/53731.
    final io.camunda.security.api.context.OidcClaimsProvider cslClaimsProvider =
        hostClaimsProvider::claimsFor;
    return new OidcTokenAuthenticationConverter(tokenClaimsConverter, cslClaimsProvider);
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
      final CamundaSecurityLibraryProperties cslProperties,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository,
      @Qualifier("oidcUserInfoHttpClient") final HttpClient oidcUserInfoHttpClient,
      final MeterRegistry meterRegistry) {
    final var oidc = cslProperties.getAuthentication().getOidc();
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
      final LazyTokenClaimsConverter tokenClaimsConverter,
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
      final CamundaSecurityLibraryProperties cslProperties) {
    return new OidcAuthenticationConfigurationRepository(cslProperties);
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

  @Bean
  public TokenValidatorFactory tokenValidatorFactory(
      final CamundaSecurityLibraryProperties cslProperties,
      final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
    // SaaS validators stay in the host. The CSL factory composes the base validator chain
    // (timestamp + optional audience) and tacks on the host's SaaS validators via extras.
    final List<OAuth2TokenValidator<Jwt>> extraValidators = new ArrayList<>();
    if (cslProperties.getSaas().isConfigured()) {
      extraValidators.add(new OrganizationValidator(cslProperties.getSaas().getOrganizationId()));
      extraValidators.add(new ClusterValidator(cslProperties.getSaas().getClusterId()));
    }
    return new TokenValidatorFactory(
        oidcAuthenticationConfigurationRepository.getOidcAuthenticationConfigurations(),
        cslProperties.getAuthentication().getOidc().getClockSkew(),
        extraValidators);
  }

  @Bean
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

  @Bean
  public OidcAccessTokenDecoderFactory accessTokenDecoderFactory(
      final JWSKeySelectorFactory jwsKeySelectorFactory,
      final TokenValidatorFactory tokenValidatorFactory) {
    return new OidcAccessTokenDecoderFactory(jwsKeySelectorFactory, tokenValidatorFactory);
  }

  @Bean
  public JwtDecoder jwtDecoder(
      final OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcAuthenticationConfigurationRepository oidcProviderRepository) {
    final var clientRegistrations = extractClientRegistrations(clientRegistrationRepository);

    final var additionalJwkSetUrisByIssuer =
        buildAdditionalJwkSetUrisByIssuer(oidcProviderRepository);

    if (clientRegistrations.size() == 1) {
      final var clientRegistration = clientRegistrations.getFirst();
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
          clientRegistrations.stream()
              .map(ClientRegistration::getRegistrationId)
              .collect(Collectors.joining(", ")));
      return new SupplierJwtDecoder(
          () ->
              oidcAccessTokenDecoderFactory.createIssuerAwareAccessTokenDecoder(
                  clientRegistrations, additionalJwkSetUrisByIssuer));
    }
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
