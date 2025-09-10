/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class OidcTokenEndpointCustomizer
    implements Customizer<OAuth2LoginConfigurer<HttpSecurity>.TokenEndpointConfig> {

  private final OidcAuthenticationConfiguration oidcConfig;

  public OidcTokenEndpointCustomizer(final SecurityConfiguration securityConfiguration) {
    oidcConfig = securityConfiguration.getAuthentication().getOidc();
  }

  @Override
  public void customize(final OAuth2LoginConfigurer<HttpSecurity>.TokenEndpointConfig config) {
    final RestClientAuthorizationCodeTokenResponseClient tokenResponseClient =
        new RestClientAuthorizationCodeTokenResponseClient();

    addResourceParameter(tokenResponseClient);

    // fallback is Spring default client_secret_basic
    if (oidcConfig.isClientAuthenticationPrivateKeyJwt()) {
      config.accessTokenResponseClient(createPrivateKeyJwtTokenResponseClient(tokenResponseClient));
    }
  }

  private RestClientAuthorizationCodeTokenResponseClient createPrivateKeyJwtTokenResponseClient(
      final RestClientAuthorizationCodeTokenResponseClient tokenResponseClient) {
    final var jwk = resolveJwk();
    final var converter =
        new NimbusJwtClientAuthenticationParametersConverter<OAuth2AuthorizationCodeGrantRequest>(
            clientRegistration -> jwk);
    converter.setJwtClientAssertionCustomizer(
        ctx -> ctx.getHeaders().algorithm(SignatureAlgorithm.RS256));

    tokenResponseClient.addParametersConverter(converter);
    return tokenResponseClient;
  }

  private void addResourceParameter(
      final RestClientAuthorizationCodeTokenResponseClient tokenResponseClient) {
    tokenResponseClient.addParametersConverter(
        request -> {
          if (oidcConfig.getResource() != null && !oidcConfig.getResource().isEmpty()) {
            final MultiValueMap<String, String> parametersToAdd = new LinkedMultiValueMap<>();
            parametersToAdd.addAll(OAuth2ParameterNames.RESOURCE, oidcConfig.getResource());
            return parametersToAdd;
          }
          return null;
        });
  }

  private JWK resolveJwk() {
    final var alias = oidcConfig.getAssertionKeystore().getKeyAlias();
    final var password = oidcConfig.getAssertionKeystore().getKeyPassword().toCharArray();
    try {
      final KeyStore keyStore = oidcConfig.getAssertionKeystore().loadKeystore();
      final var pk = (PrivateKey) keyStore.getKey(alias, password);
      final var cert = keyStore.getCertificate(alias);
      final var pub = (RSAPublicKey) cert.getPublicKey();
      final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      final byte[] digest = sha256.digest(pub.getEncoded());

      return new RSAKey.Builder(pub)
          .privateKey(pk)
          .x509CertChain(List.of(Base64.encode(cert.getEncoded())))
          // TODO needs to be customizable for requirements by different IdPs
          .keyID(String.valueOf(Base64URL.encode(digest)))
          .build();
    } catch (final Exception e) {
      throw new IllegalStateException("Unable to load keystore", e);
    }
  }
}
