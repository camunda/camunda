/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

@ExtendWith(MockitoExtension.class)
class OptimizeCcsmSecurityConfigurationTest {

  @Mock private OidcProviderConfigurationPort oidcProviderConfigurationPort;
  @Mock private CCSMTokenService ccsmTokenService;

  private final OptimizeCcsmSecurityConfiguration config = new OptimizeCcsmSecurityConfiguration();
  private final CamundaSecurityLibraryProperties cslProperties =
      new CamundaSecurityLibraryProperties();

  private OAuth2TokenValidator<Jwt> sharedValidator() {
    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations()).thenReturn(Map.of());

    final TokenValidatorFactory factory =
        config.tokenValidatorFactory(
            oidcProviderConfigurationPort, cslProperties, ccsmTokenService);
    return factory.createTokenValidator(clientRegistration());
  }

  @Test
  void shouldAcceptTokenGrantedTheOptimizePermission() {
    // given
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    // when
    final boolean hasErrors = validator.validate(jwt()).hasErrors();

    // then
    assertThat(hasErrors).isFalse();
  }

  @Test
  void shouldRejectTokenIdentityCannotVerify() {
    // given
    // Restores the CCSM Identity write:* gate CSL otherwise skips entirely: without this
    // TokenValidatorFactory override, CSL's default only checks issuer/signature/expiry.
    doThrow(new TokenVerificationException("token invalid"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    // when
    final boolean hasErrors = validator.validate(jwt()).hasErrors();

    // then
    assertThat(hasErrors).isTrue();
  }

  @Test
  void shouldNotRegisterIdTokenDecoderFactoryBean() {
    // given
    // The login id_token is audienced to the OIDC client-id, not camunda.identity.audience, so
    // routing it through OptimizeIdentityPermissionValidator would reject every real login (see
    // class javadoc). Unlike the reflection-based check this replaces (which only proved the
    // method wasn't declared), this proves the actual runtime outcome: no bean of the id_token
    // decoder factory type is contributed by this configuration, so Spring's stock decoder stays
    // in charge of the id_token, exactly as it would for CCSaaS if
    // OptimizeCloudSecurityConfiguration
    // did not declare its own idTokenDecoderFactory bean.
    final ApplicationContextRunner runner =
        new ApplicationContextRunner()
            // The chain/filter beans in OptimizeCcsmSecurityConfiguration depend on HttpSecurity
            // and OIDC client-registration beans this minimal context doesn't provide. Registering
            // LazyInitializationBeanFactoryPostProcessor as a bean is sufficient to suppress the
            // resulting NoSuchBeanDefinitionException: a plain ApplicationContextRunner still runs
            // the full bean-factory-post-processor phase on refresh, so this bean's own
            // postProcessBeanFactory is invoked automatically and marks every bean definition
            // lazy — without it (confirmed by temporarily removing it) this test fails to start the
            // context, because the HttpSecurity-dependent beans get eagerly instantiated. This is
            // the same pattern CslSecurityChainSelectionTest uses; the alternative of never
            // registering those beans isn't available here because they live in the same
            // @Configuration class as the bean this test actually needs.
            .withBean(
                LazyInitializationBeanFactoryPostProcessor.class,
                LazyInitializationBeanFactoryPostProcessor::new)
            .withBean(CamundaSecurityLibraryProperties.class, CamundaSecurityLibraryProperties::new)
            .withBean(OidcProviderConfigurationPort.class, () -> oidcProviderConfigurationPort)
            .withBean(CCSMTokenService.class, () -> ccsmTokenService)
            .withUserConfiguration(OptimizeCcsmSecurityConfiguration.class);
    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations()).thenReturn(Map.of());

    // when / then
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(TokenValidatorFactory.class)).isNotNull();
          assertThat(context).getBeanNames(JwtDecoderFactory.class).isEmpty();
        });
  }

  private static ClientRegistration clientRegistration() {
    return ClientRegistration.withRegistrationId("oidc")
        .clientId("optimize")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/api/authentication/callback")
        .authorizationUri("http://idp/authorize")
        .tokenUri("http://idp/token")
        .build();
  }

  private static Jwt jwt() {
    final Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .subject("user")
        .build();
  }
}
