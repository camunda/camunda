/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

public class ClientAssertionConstantsTest {

  @Test
  void shouldHaveCorrectOAuth2Constants() {
    // OAuth2 client assertion constants
    assertThat(ClientAssertionConstants.CLIENT_ASSERTION_TYPE_JWT_BEARER)
        .isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
    assertThat(ClientAssertionConstants.CLIENT_ASSERTION_TYPE_PARAM)
        .isEqualTo("client_assertion_type");
    assertThat(ClientAssertionConstants.CLIENT_ASSERTION_PARAM).isEqualTo("client_assertion");
    assertThat(ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE)
        .isEqualTo("client_credentials");
    assertThat(ClientAssertionConstants.CLIENT_ASSERTION_GRANT_TYPE_PARAM).isEqualTo("grant_type");
  }

  @Test
  void shouldHaveCorrectOidcConstants() {
    assertThat(ClientAssertionConstants.OIDC_REGISTRATION_ID).isEqualTo("oidc");
  }

  @Test
  void shouldHaveCorrectCertificateUserConstants() {
    assertThat(ClientAssertionConstants.CERT_USER_ID).isEqualTo("certificate-user");
    assertThat(ClientAssertionConstants.CERT_USER_NAME).isEqualTo("Certificate User");
    assertThat(ClientAssertionConstants.CERT_USER_EMAIL)
        .isEqualTo("certificate-user@camunda.local");
    assertThat(ClientAssertionConstants.CERT_USER_ROLE).isEqualTo("ROLE_USER");
  }

  @Test
  void shouldHaveCorrectSecurityConstants() {
    assertThat(ClientAssertionConstants.SESSION_KEY).isEqualTo("camunda.certificate.auth");
  }

  @Test
  void shouldHaveCorrectCertificateConstants() {
    assertThat(ClientAssertionConstants.KEYSTORE_TYPE_PKCS12).isEqualTo("PKCS12");
    assertThat(ClientAssertionConstants.HASH_ALGORITHM_SHA1).isEqualTo("SHA-1");
  }

  @Test
  void shouldBeUtilityClass() throws Exception {
    // Verify class is final
    assertThat(Modifier.isFinal(ClientAssertionConstants.class.getModifiers())).isTrue();

    // Verify constructor is private
    final Constructor<ClientAssertionConstants> constructor =
        ClientAssertionConstants.class.getDeclaredConstructor();
    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

    // Verify constructor throws when called
    constructor.setAccessible(true);
    constructor.newInstance();
  }
}
