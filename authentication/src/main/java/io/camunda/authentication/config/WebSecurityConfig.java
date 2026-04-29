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
import io.camunda.authentication.converter.UsernamePasswordAuthenticationTokenConverter;
import io.camunda.authentication.exception.BasicAuthenticationNotSupportedException;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.autoconfigure.spring.security.OidcResourceServerCustomizer;
import io.camunda.security.configuration.AuthenticationConfiguration;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.ProvidersConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.adapter.SecurityPathAdapter;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.oidc.CachingOidcClaimsProvider;
import io.camunda.security.oidc.NoopOidcClaimsProvider;
import io.camunda.security.oidc.OidcClaimsProvider;
import io.camunda.security.oidc.OidcUserInfoClient;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer.ProtectedResourceMetadataConfigurer;
import org.springframework.security.config.observation.SecurityObservationSettings;
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
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Camunda host wiring for the central security filter chains. The CSL v2 chain auto-configurations
 * activate via {@code camunda.security.authentication.method} — no {@code @Import} required.
 * Registers a {@link SecurityPathAdapter} for the monorepo's path topology and contributes
 * camunda-specific extensions through the library's extension hooks ({@link
 * OidcResourceServerCustomizer}).
 */
@Configuration
@EnableWebSecurity
@Profile("consolidated-auth")
public class WebSecurityConfig {

  public static final String REDIRECT_URI = "/sso-callback";
  public static final String SESSION_COOKIE = "camunda-session";

  private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfig.class);

  private static final String CAMUNDA_AUTHENTICATION_OBSERVATION_NAME =
      "camunda_authentication_external_requests";
  private static final KeyValues CAMUNDA_AUTHENTICATION_OBSERVATION_DOMAIN_IDENTITY_TAGS =
      KeyValues.of("domain", "identity");

  @Bean
  public SecurityObservationSettings defaultSecurityObservations() {
    return SecurityObservationSettings.withDefaults().build();
  }

  @Bean
  public SecurityPathAdapter securityPathAdapter() {
    return new CamundaSecurityPathAdapter();
  }

  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnSecondaryStorageEnabled
  public static class BasicConfiguration {

    private final SecurityConfiguration securityConfiguration;

    public BasicConfiguration(final SecurityConfiguration securityConfiguration) {
      this.securityConfiguration = securityConfiguration;
    }

    @PostConstruct
    public void verifyBasicConfiguration() {
      if (isOidcConfigurationEnabled(securityConfiguration)) {
        throw new IllegalStateException(
            "Oidc configuration is not supported with `BASIC` authentication method");
      }
    }

    private static boolean isOidcConfigurationEnabled(
        final SecurityConfiguration securityConfiguration) {
      if (securityConfiguration.getAuthentication().getOidc() != null
          && securityConfiguration.getAuthentication().getOidc().isSet()) {
        return true;
      }
      return Optional.ofNullable(securityConfiguration.getAuthentication())
          .map(AuthenticationConfiguration::getProviders)
          .map(ProvidersConfiguration::getOidc)
          .map(Map::values)
          .map(values -> values.stream().anyMatch(OidcAuthenticationConfiguration::isSet))
          .orElse(false);
    }

    @Bean
    public CamundaAuthenticationConverter<Authentication> usernamePasswordAuthenticationConverter(
        final RoleServices roleServices,
        final GroupServices groupServices,
        final TenantServices tenantServices) {
      return new UsernamePasswordAuthenticationTokenConverter(
          roleServices, groupServices, tenantServices);
    }
  }

  /**
   * Fail-fast configuration when Basic Authentication is configured but secondary storage is
   * disabled (camunda.database.type=none). Prevents misleading security flows.
   */
  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnSecondaryStorageDisabled
  public static class BasicAuthenticationNoDbConfiguration {

    @Bean
    public BasicAuthenticationNoDbFailFastBean basicAuthenticationNoDbFailFastBean() {
      throw new BasicAuthenticationNotSupportedException();
    }
  }

  /** Marker bean for Basic Auth no-db fail-fast configuration. */
  public static class BasicAuthenticationNoDbFailFastBean {}

  @Configuration
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  public static class OidcConfiguration {

    private final SecurityConfiguration securityConfiguration;

    public OidcConfiguration(final SecurityConfiguration securityConfiguration) {
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
        final io.camunda.authentication.service.MembershipService membershipService) {
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
     * app-wide registry wins via {@link ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry oidcFallbackMeterRegistry() {
      return new SimpleMeterRegistry();
    }

    /**
     * HTTP client used by {@link OidcClaimsProvider} implementations to call the IdP's {@code
     * /userinfo} endpoint.
     */
    @Bean(destroyMethod = "close", name = "oidcUserInfoHttpClient")
    @ConditionalOnMissingBean(name = "oidcUserInfoHttpClient")
    public HttpClient oidcUserInfoHttpClient(
        @Autowired(required = false) final SslBundles sslBundles) {
      // Redirects are NOT followed. The JDK HttpClient would re-send the Authorization: Bearer
      // header to the redirect target, leaking the access token if the IdP's /userinfo responded
      // with a 3xx (misconfiguration, open-redirect vuln, hijacked CDN).
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
        final String registrationId, final OidcAuthenticationConfiguration configuration) {
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

    @Bean
    public TokenValidatorFactory tokenValidatorFactory(
        final SecurityConfiguration securityConfiguration,
        final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
      return new TokenValidatorFactory(
          securityConfiguration, oidcAuthenticationConfigurationRepository);
    }

    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
        final TokenValidatorFactory tokenValidatorFactory,
        final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository,
        final ClientRegistrationRepository clientRegistrationRepository) {
      final var decoderFactory = new OidcIdTokenDecoderFactory();
      decoderFactory.setJwtValidatorFactory(tokenValidatorFactory::createTokenValidator);
      final Map<String, OidcAuthenticationConfiguration> oidcAuthenticationConfigurations =
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
            additionalJwkSetUrisByIssuer.get(
                clientRegistration.getProviderDetails().getIssuerUri());
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
                  OidcAuthenticationConfiguration::getIssuerUri,
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
    public OAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository() {
      return new HttpSessionOAuth2AuthorizedClientRepository();
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

      // Build a refresh token flow client manually to add support for private_key_jwt.
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

      final var restTemplate = new RestTemplate();
      restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
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

    @Bean
    @ConditionalOnSecondaryStorageEnabled
    public LogoutSuccessHandler oidcLogoutSuccessHandler(
        final WebappRedirectStrategy redirectStrategy,
        final ClientRegistrationRepository repository,
        final SecurityConfiguration config) {
      final var oidcConfig = config.getAuthentication().getOidc();
      if (!oidcConfig.isIdpLogoutEnabled()) {
        return new NoContentLogoutHandler();
      }
      final var handler = new CamundaOidcLogoutSuccessHandler(repository);
      handler.setPostLogoutRedirectUri("{baseUrl}/post-logout");
      handler.setRedirectStrategy(redirectStrategy);
      return handler;
    }

    /**
     * RFC 9728 Protected Resource Metadata customizer — applied to both OIDC API and OIDC webapp
     * resource-server configurers via the library hook.
     */
    @Bean
    public OidcResourceServerCustomizer protectedResourceMetadataCustomizer(
        final ClientRegistrationRepository clientRegistrationRepository) {
      final var issuerUris =
          extractClientRegistrations(clientRegistrationRepository).stream()
              .map(clientRegistration -> clientRegistration.getProviderDetails().getIssuerUri())
              .filter(Objects::nonNull)
              .distinct()
              .toList();
      return oauth2 ->
          oauth2.protectedResourceMetadata(
              (Customizer<ProtectedResourceMetadataConfigurer>)
                  prmConfigurer ->
                      prmConfigurer.protectedResourceMetadataCustomizer(
                          prmBuilder -> issuerUris.forEach(prmBuilder::authorizationServer)));
    }

    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
        final ClientRegistrationRepository clientRegistrationRepository,
        final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
      return new ClientAwareOAuth2AuthorizationRequestResolver(
          clientRegistrationRepository, oidcAuthenticationConfigurationRepository);
    }

    private RestClient restClient(final ObservationRegistry observationRegistry) {
      return RestClient.builder()
          .observationConvention(
              new CustomDefaultClientRequestObservationConvention(
                  CAMUNDA_AUTHENTICATION_OBSERVATION_NAME,
                  CAMUNDA_AUTHENTICATION_OBSERVATION_DOMAIN_IDENTITY_TAGS))
          .messageConverters(
              messageConverters -> {
                messageConverters.clear();
                messageConverters.add(new FormHttpMessageConverter());
                messageConverters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
              })
          .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
          .observationRegistry(observationRegistry)
          .build();
    }
  }

  /**
   * Logout success handler that returns 204 No Content. Used when no IdP-coordinated logout is
   * configured. The library's webapp chain calls this via the {@code LogoutSuccessHandler}
   * ObjectProvider hook.
   */
  protected static final class NoContentLogoutHandler
      implements AuthenticationSuccessHandler, LogoutSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
        final jakarta.servlet.http.HttpServletRequest request,
        final jakarta.servlet.http.HttpServletResponse response,
        final Authentication authentication) {
      response.setStatus(org.springframework.http.HttpStatus.NO_CONTENT.value());
    }

    @Override
    public void onLogoutSuccess(
        final jakarta.servlet.http.HttpServletRequest request,
        final jakarta.servlet.http.HttpServletResponse response,
        final Authentication authentication) {
      response.setStatus(org.springframework.http.HttpStatus.NO_CONTENT.value());
    }
  }
}
