/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Discovery when the configured {@code issuer-uri} and the issuer in the provider's discovery
 * document differ by a trailing slash. Spring requires the two to be identical, so this used to
 * fail startup (camunda/camunda#61896).
 */
class ClientRegistrationFactoryIssuerTrailingSlashTest {

  @RegisterExtension
  static WireMockExtension idp =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final String REALM_PATH = "/realms/camunda";

  /** Entra ID and Okta: the document has no trailing slash, the configuration does. */
  @Test
  void shouldTakeTheProvidersIssuerWhenTheConfiguredOneHasAnExtraTrailingSlash() {
    // given
    final var issuer = baseUrl() + REALM_PATH;
    givenDiscoveryDocumentDeclaring(issuer);

    // when
    final var registration =
        ClientRegistrationFactory.createClientRegistration("idp", configuredWith(issuer + "/"));

    // then - the provider's form, which is what its tokens carry
    assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(issuer);
  }

  /** Auth0: the document has a trailing slash, the configuration doesn't. */
  @Test
  void shouldTakeTheProvidersIssuerWhenTheConfiguredOneIsMissingItsTrailingSlash() {
    // given
    final var issuer = baseUrl() + REALM_PATH;
    givenDiscoveryDocumentDeclaring(issuer + "/");

    // when
    final var registration =
        ClientRegistrationFactory.createClientRegistration("idp", configuredWith(issuer));

    // then
    assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(issuer + "/");
  }

  @Test
  void shouldKeepTheConfiguredIssuerWhenItMatchesTheProvidersExactly() {
    // given
    final var issuer = baseUrl() + REALM_PATH;
    givenDiscoveryDocumentDeclaring(issuer);

    // when
    final var registration =
        ClientRegistrationFactory.createClientRegistration("idp", configuredWith(issuer));

    // then
    assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(issuer);
  }

  /**
   * Only a trailing slash is tolerated; any other difference still fails, with Spring's message.
   */
  @Test
  void shouldStillFailWhenTheIssuersDifferByMoreThanATrailingSlash() {
    // given
    final var issuer = baseUrl() + REALM_PATH;
    givenDiscoveryDocumentDeclaring(baseUrl() + "/realms/other");

    // when / then
    assertThatThrownBy(
            () -> ClientRegistrationFactory.createClientRegistration("idp", configuredWith(issuer)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("did not match the requested issuer \"" + issuer + "\"");
  }

  /**
   * The first document declares an unrelated issuer, and a second fetch, for the slash variant,
   * returns a document that declares the variant. The retry must not paper over the first, real
   * mismatch: only a document that declared the slash variant itself justifies retrying.
   */
  @Test
  void shouldNotRetryWhenTheFirstDocumentDeclaredAnUnrelatedIssuer() {
    // given
    final var issuer = baseUrl() + REALM_PATH;
    givenDiscoveryDocumentsInSequence(baseUrl() + "/realms/other", issuer + "/");

    // when / then - the original mismatch, against the unrelated issuer
    assertThatThrownBy(
            () -> ClientRegistrationFactory.createClientRegistration("idp", configuredWith(issuer)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("The Issuer \"" + baseUrl() + "/realms/other\"");
  }

  /**
   * Spring's message is the only source of the declared issuer; a change to its form must make the
   * retry fall back to failing, never to accepting.
   */
  @Test
  void shouldReadTheDeclaredIssuerFromSpringsMismatchMessage() {
    assertThat(
            ClientRegistrationFactory.declaredIssuer(
                new IllegalStateException(
                    "The Issuer \"https://idp/v2.0\" provided in the configuration metadata did"
                        + " not match the requested issuer \"https://idp/v2.0/\"")))
        .isEqualTo("https://idp/v2.0");
    assertThat(ClientRegistrationFactory.declaredIssuer(new IllegalStateException("other")))
        .isNull();
    assertThat(ClientRegistrationFactory.declaredIssuer(new IllegalStateException())).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "https://login.microsoftonline.com/tenant/v2.0/, https://login.microsoftonline.com/tenant/v2.0",
    "https://login.microsoftonline.com/tenant/v2.0, https://login.microsoftonline.com/tenant/v2.0/",
    "https://dev-1234.okta.com/, https://dev-1234.okta.com",
    "https://tenant.auth0.com, https://tenant.auth0.com/"
  })
  void shouldToggleTheTrailingSlash(final String issuer, final String expected) {
    assertThat(ClientRegistrationFactory.withToggledTrailingSlash(issuer)).isEqualTo(expected);
  }

  /** Nothing that isn't a plain issuer URL, and never down to a bare scheme. */
  @ParameterizedTest
  @ValueSource(strings = {"https://", "https://idp//", "https://idp/?x=1", "https://idp/#f", "/"})
  void shouldNotToggleAValueThatIsNotAPlainIssuerUrl(final String issuer) {
    assertThat(ClientRegistrationFactory.withToggledTrailingSlash(issuer)).isNull();
  }

  private static String baseUrl() {
    return "http://localhost:" + idp.getPort();
  }

  /** Serves the document for both forms of the realm path, as a real provider would. */
  private static void givenDiscoveryDocumentDeclaring(final String documentIssuer) {
    final var base = baseUrl();
    final var document =
        """
        {
          "issuer": "%s",
          "authorization_endpoint": "%s/auth",
          "token_endpoint": "%s/token",
          "jwks_uri": "%s/jwks",
          "response_types_supported": ["code"],
          "subject_types_supported": ["public"],
          "id_token_signing_alg_values_supported": ["RS256"]
        }
        """
            .formatted(documentIssuer, base, base, base);
    for (final var path :
        new String[] {
          REALM_PATH + "/.well-known/openid-configuration",
          REALM_PATH + "//.well-known/openid-configuration"
        }) {
      idp.stubFor(
          get(urlEqualTo(path))
              .willReturn(
                  aResponse().withHeader("Content-Type", "application/json").withBody(document)));
    }
  }

  /**
   * The first discovery request gets a document declaring one issuer, every later one the other.
   */
  private static void givenDiscoveryDocumentsInSequence(
      final String firstIssuer, final String laterIssuer) {
    for (final var path :
        new String[] {
          REALM_PATH + "/.well-known/openid-configuration",
          REALM_PATH + "//.well-known/openid-configuration"
        }) {
      idp.stubFor(
          get(urlEqualTo(path))
              .inScenario("discovery")
              .whenScenarioStateIs(Scenario.STARTED)
              .willReturn(document(firstIssuer))
              .willSetStateTo("fetched"));
      idp.stubFor(
          get(urlEqualTo(path))
              .inScenario("discovery")
              .whenScenarioStateIs("fetched")
              .willReturn(document(laterIssuer)));
    }
  }

  private static ResponseDefinitionBuilder document(final String documentIssuer) {
    final var base = baseUrl();
    return aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(
            """
            {
              "issuer": "%s",
              "authorization_endpoint": "%s/auth",
              "token_endpoint": "%s/token",
              "jwks_uri": "%s/jwks",
              "response_types_supported": ["code"],
              "subject_types_supported": ["public"],
              "id_token_signing_alg_values_supported": ["RS256"]
            }
            """
                .formatted(documentIssuer, base, base, base));
  }

  private static OidcAuthenticationConfiguration configuredWith(final String issuerUri) {
    final var configuration = new OidcAuthenticationConfiguration();
    configuration.setIssuerUri(issuerUri);
    configuration.setClientId("client");
    configuration.setClientSecret("secret");
    return configuration;
  }
}
