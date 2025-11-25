/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.security.headers;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY_REPORT_ONLY;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_OPENER_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY;
import static com.google.common.net.HttpHeaders.EXPIRES;
import static com.google.common.net.HttpHeaders.PERMISSIONS_POLICY;
import static com.google.common.net.HttpHeaders.PRAGMA;
import static com.google.common.net.HttpHeaders.REFERRER_POLICY;
import static com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS;
import static com.google.common.net.HttpHeaders.X_FRAME_OPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.configuration.headers.ContentSecurityPolicyConfig;
import io.camunda.security.configuration.headers.PermissionsPolicyConfig;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.testcontainers.junit.jupiter.Container;

@ZeebeIntegration
public class SecurityHeadersOidcIT extends SecurityHeadersBaseIT {
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String KEYCLOAK_REALM = "camunda";
  private static final String CLIENT_ID = "zeebe";
  private static final String CLIENT_SECRET = "secret";
  private static final String CLIENT_AUTHENTICATOR_TYPE = "client-secret";
  private static final String USER_ID_CLAIM_NAME = "sub";
  private static final String EXAMPLE_CLIENT_ID = "example";
  private static final String EXAMPLE_REDIRECT_URI = "example.com";
  private static final String ADMIN_ROLE = "admin";
  private static final String USERS_KEY = "users";
  private static final String USERNAME = "zeebe-service-account";
  private static final String AUDIENCE = "zeebe";
  private static final String CACHE_FILE_NAME = "default";

  private static final String REALMS_PATH = "/realms/";
  private static final String OIDC_TOKEN_PATH = "/protocol/openid-connect/token";

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String ACCESS_TOKEN_FIELD = "access_token";
  private static final String CLIENT_CREDENTIALS_PARAM = "grant_type";
  private static final String CLIENT_ID_PARAM = "client_id";
  private static final String CLIENT_SECRET_PARAM = "client_secret";

  @Container
  private static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  @AutoClose private static CamundaClient camundaClient;

  @TestZeebe(awaitCompleteTopology = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withCamundaExporter("http://" + CONTAINER.getHttpHostAddress())
          .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
          .withUnifiedConfig(
              cfg ->
                  cfg.getData()
                      .getSecondaryStorage()
                      .getElasticsearch()
                      .setUrl("http://" + CONTAINER.getHttpHostAddress()))
          .withSecurityConfig(
              c -> {
                c.getAuthorizations().setEnabled(true);

                final var oidcConfig = c.getAuthentication().getOidc();
                oidcConfig.setIssuerUri(buildKeycloakIssuerUri());
                oidcConfig.setClientId(EXAMPLE_CLIENT_ID);
                oidcConfig.setRedirectUri(EXAMPLE_REDIRECT_URI);

                c.getInitialization()
                    .setMappingRules(
                        List.of(new ConfiguredMappingRule(USER_ID, USER_ID_CLAIM_NAME, USER_ID)));
                c.getInitialization()
                    .getDefaultRoles()
                    .put(ADMIN_ROLE, Map.of(USERS_KEY, List.of(USER_ID)));
              });

  @BeforeAll
  static void setupKeycloak() {
    final var client = createClientRepresentation();
    final var user = createUserRepresentation();
    final var realm = createRealmRepresentation(client, user);

    try (final var keycloak = KEYCLOAK.getKeycloakAdminClient()) {
      keycloak.realms().create(realm);
    }
  }

  @BeforeEach
  void beforeEach(@TempDir final Path tempDir) {
    camundaClient = createCamundaClient(tempDir);
  }

  @Override
  protected CamundaClient getCamundaClient() {
    return camundaClient;
  }

  @Override
  protected HttpResponse<String> makeAuthenticatedRequest(final String path) throws Exception {
    final var bearerToken = getBearerToken();
    final var request =
        HttpRequest.newBuilder()
            .uri(createUri(camundaClient, path))
            .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + bearerToken)
            .build();

    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Override
  protected void assertSecurityHeaders(final Map<String, List<String>> headers) {
    assertThat(headers)
        .containsEntry(X_CONTENT_TYPE_OPTIONS, List.of(X_CONTENT_TYPE_OPTIONS_VALUE));
    assertThat(headers).containsEntry(CACHE_CONTROL, List.of(CACHE_CONTROL_VALUE));
    assertThat(headers).containsEntry(PRAGMA, List.of(PRAGMA_VALUE));
    assertThat(headers).containsEntry(EXPIRES, List.of(EXPIRES_VALUE));
    assertThat(headers).containsEntry(X_FRAME_OPTIONS, List.of(X_FRAME_OPTIONS_VALUE));
    assertThat(headers)
        .containsEntry(
            CONTENT_SECURITY_POLICY,
            List.of(ContentSecurityPolicyConfig.DEFAULT_SM_SECURITY_POLICY));
    assertThat(headers).doesNotContainKey(CONTENT_SECURITY_POLICY_REPORT_ONLY);
    assertThat(headers).containsEntry(REFERRER_POLICY, List.of(REFERRER_POLICY_VALUE));
    assertThat(headers)
        .containsEntry(CROSS_ORIGIN_OPENER_POLICY, List.of(CROSS_ORIGIN_OPENER_POLICY_VALUE));
    assertThat(headers)
        .containsEntry(CROSS_ORIGIN_EMBEDDER_POLICY, List.of(CROSS_ORIGIN_EMBEDDER_POLICY_VALUE));
    assertThat(headers)
        .containsEntry(CROSS_ORIGIN_RESOURCE_POLICY, List.of(CROSS_ORIGIN_RESOURCE_POLICY_VALUE));
    assertThat(headers)
        .containsEntry(
            PERMISSIONS_POLICY, List.of(PermissionsPolicyConfig.DEFAULT_PERMISSIONS_POLICY_VALUE));
  }

  private String getBearerToken() throws Exception {
    return getBearerToken(KEYCLOAK.getAuthServerUrl(), KEYCLOAK_REALM, CLIENT_ID, CLIENT_SECRET);
  }

  public String getBearerToken(
      final String keycloakUrl,
      final String realm,
      final String clientId,
      final String clientSecret)
      throws Exception {
    final var client = HttpClient.newHttpClient();
    final var tokenUrl = buildTokenUrl(keycloakUrl, realm);
    final var formData = buildFormData(clientId, clientSecret);

    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(formData))
            .build();

    final var response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == OK.value()) {
      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode jsonNode = mapper.readTree(response.body());
      return jsonNode.get(ACCESS_TOKEN_FIELD).asText();
    } else {
      throw new RuntimeException(
          "Failed to get token: " + response.statusCode() + " - " + response.body());
    }
  }

  private static ClientRepresentation createClientRepresentation() {
    final var client = new ClientRepresentation();
    client.setClientId(CLIENT_ID);
    client.setEnabled(true);
    client.setClientAuthenticatorType(CLIENT_AUTHENTICATOR_TYPE);
    client.setSecret(CLIENT_SECRET);
    client.setServiceAccountsEnabled(true);
    return client;
  }

  private static UserRepresentation createUserRepresentation() {
    final var user = new UserRepresentation();
    user.setId(USER_ID);
    user.setUsername(USERNAME);
    user.setServiceAccountClientId(CLIENT_ID);
    user.setEnabled(true);
    return user;
  }

  private static RealmRepresentation createRealmRepresentation(
      final ClientRepresentation client, final UserRepresentation user) {
    final var realm = new RealmRepresentation();
    realm.setRealm(KEYCLOAK_REALM);
    realm.setEnabled(true);
    realm.setClients(List.of(client));
    realm.setUsers(List.of(user));
    return realm;
  }

  private CamundaClient createCamundaClient(final Path tempDir) {
    return CamundaClient.newClientBuilder()
        .grpcAddress(broker.grpcAddress())
        .restAddress(broker.restAddress())
        .preferRestOverGrpc(true)
        .defaultRequestTimeout(Duration.ofSeconds(15))
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl(buildTokenUrl(KEYCLOAK.getAuthServerUrl(), KEYCLOAK_REALM))
                .credentialsCachePath(tempDir.resolve(CACHE_FILE_NAME).toString())
                .build())
        .build();
  }

  private static String buildKeycloakIssuerUri() {
    return KEYCLOAK.getAuthServerUrl() + REALMS_PATH + KEYCLOAK_REALM;
  }

  private static String buildTokenUrl(final String keycloakUrl, final String realm) {
    return keycloakUrl + REALMS_PATH + realm + OIDC_TOKEN_PATH;
  }

  private static String buildFormData(final String clientId, final String clientSecret) {
    return CLIENT_CREDENTIALS_PARAM
        + "="
        + URLEncoder.encode(
            AuthorizationGrantType.CLIENT_CREDENTIALS.getValue(), StandardCharsets.UTF_8)
        + "&"
        + CLIENT_ID_PARAM
        + "="
        + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
        + "&"
        + CLIENT_SECRET_PARAM
        + "="
        + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
  }
}
