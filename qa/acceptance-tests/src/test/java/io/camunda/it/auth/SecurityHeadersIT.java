/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_OPENER_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY;
import static com.google.common.net.HttpHeaders.EXPIRES;
import static com.google.common.net.HttpHeaders.PERMISSIONS_POLICY;
import static com.google.common.net.HttpHeaders.PRAGMA;
import static com.google.common.net.HttpHeaders.REFERRER_POLICY;
import static com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS;
import static com.google.common.net.HttpHeaders.X_FRAME_OPTIONS;
import static com.google.common.net.HttpHeaders.X_XSS_PROTECTION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.secureheaders.CacheControlConfig;
import io.camunda.security.configuration.secureheaders.ContentSecurityPolicyConfig;
import io.camunda.security.configuration.secureheaders.ContentTypeOptionsConfig;
import io.camunda.security.configuration.secureheaders.CrossOriginEmbedderPolicyConfig;
import io.camunda.security.configuration.secureheaders.CrossOriginOpenerPolicyConfig;
import io.camunda.security.configuration.secureheaders.CrossOriginResourcePolicyConfig;
import io.camunda.security.configuration.secureheaders.FrameOptionsConfig;
import io.camunda.security.configuration.secureheaders.PermissionsPolicyConfig;
import io.camunda.security.configuration.secureheaders.ReferrerPolicyConfig;
import io.camunda.security.configuration.secureheaders.SecurityHeaderConfigurations;
import io.camunda.security.configuration.secureheaders.XssConfig;
import io.camunda.security.configuration.secureheaders.values.CrossOriginEmbedderPolicy;
import io.camunda.security.configuration.secureheaders.values.CrossOriginOpenerPolicy;
import io.camunda.security.configuration.secureheaders.values.CrossOriginResourcePolicy;
import io.camunda.security.configuration.secureheaders.values.ReferrerPolicy;
import io.camunda.security.configuration.secureheaders.values.XssHeaderModes;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class SecurityHeadersIT {

  private static final String URL_TO_VISIT = "v2/authentication/me";

  private static final String USERNAME = "correct_username";
  private static final String PASSWORD = "correct_password";

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    return new URI("%s%s".formatted(client.getConfiguration().getRestAddress(), path));
  }

  private static String basicAuthentication() {
    return "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
  }

  @Nested
  public class DefaultSecurityHeadersIT {

    @MultiDbTestApplication
    private static final TestStandaloneBroker BROKER =
        new TestStandaloneBroker().withBasicAuth().withAuthenticatedAccess();

    @UserDefinition private static final User USER = new User(USERNAME, PASSWORD, List.of());
    private static CamundaClient camundaClient;

    @Test
    void testDefaultHeaders(@Authenticated(USERNAME) final CamundaClient userClient)
        throws Exception {
      // when
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(createUri(camundaClient, URL_TO_VISIT))
              .header(HttpHeaders.AUTHORIZATION, basicAuthentication())
              .build();
      final HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

      // then
      assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

      final var headers = response.headers().map();
      assertThat(headers).containsEntry(X_CONTENT_TYPE_OPTIONS, List.of("nosniff"));
      assertThat(headers).containsEntry(X_XSS_PROTECTION, List.of("1"));
      assertThat(headers)
          .containsEntry(CACHE_CONTROL, List.of("no-cache, no-store, max-age=0, must-revalidate"));
      assertThat(headers).containsEntry(PRAGMA, List.of("no-cache"));
      assertThat(headers).containsEntry(EXPIRES, List.of("0"));
      assertThat(headers).containsEntry(X_FRAME_OPTIONS, List.of("SAMEORIGIN"));
      assertThat(headers)
          .containsEntry(
              CONTENT_SECURITY_POLICY,
              List.of(ContentSecurityPolicyConfig.DEFAULT_SM_SECURITY_POLICY));
      assertThat(headers)
          .containsEntry(REFERRER_POLICY, List.of("strict-origin-when-cross-origin"));
      assertThat(headers).doesNotContainKey(PERMISSIONS_POLICY);
      assertThat(headers)
          .containsEntry(CROSS_ORIGIN_OPENER_POLICY, List.of("same-origin-allow-popups"));
      assertThat(headers).containsEntry(CROSS_ORIGIN_EMBEDDER_POLICY, List.of("require-corp"));
      assertThat(headers).containsEntry(CROSS_ORIGIN_RESOURCE_POLICY, List.of("same-origin"));
    }
  }

  @Nested
  public class ModifiedSecurityHeadersIT {

    @MultiDbTestApplication
    private static final TestStandaloneBroker BROKER =
        new TestStandaloneBroker()
            .withBasicAuth()
            .withAuthenticatedAccess()
            .withSecurityConfig(
                props -> {
                  final var headersCfg = new SecurityHeaderConfigurations();

                  final var contentTypeOptionsCfg = new ContentTypeOptionsConfig();
                  contentTypeOptionsCfg.setEnabled(false);
                  headersCfg.setContentTypeOptionsConfig(contentTypeOptionsCfg);

                  final var xssConfig = new XssConfig();
                  xssConfig.setMode(XssHeaderModes.DISABLED);
                  headersCfg.setXssConfig(xssConfig);

                  final var cacheControlCfg = new CacheControlConfig();
                  cacheControlCfg.setEnabled(false);
                  headersCfg.setCacheConfig(cacheControlCfg);

                  final var frameOptionsCfg = new FrameOptionsConfig();
                  frameOptionsCfg.setEnabled(false);
                  headersCfg.setFrameOptionsConfig(frameOptionsCfg);

                  final var contentSecurityPolicyConfig = new ContentSecurityPolicyConfig();
                  contentSecurityPolicyConfig.setEnabled(true);
                  contentSecurityPolicyConfig.setPolicyDirectives("self; camunda.com");
                  headersCfg.setContentSecurityPolicyConfig(contentSecurityPolicyConfig);

                  final var referrerPolicyConfig = new ReferrerPolicyConfig();
                  referrerPolicyConfig.setReferrerPolicy(ReferrerPolicy.NO_REFERRER);
                  headersCfg.setReferrerPolicyConfig(referrerPolicyConfig);

                  final var permissionsPolicyConfig = new PermissionsPolicyConfig();
                  permissionsPolicyConfig.setPolicy("camera=*");
                  headersCfg.setPermissionsPolicyConfig(permissionsPolicyConfig);

                  final var crossOriginOpenerPolicyConfig = new CrossOriginOpenerPolicyConfig();
                  crossOriginOpenerPolicyConfig.setCrossOriginOpenerPolicy(
                      CrossOriginOpenerPolicy.UNSAFE_NONE);
                  headersCfg.setCrossOriginOpenerPolicyConfig(crossOriginOpenerPolicyConfig);

                  final var crossOriginEmbedderPolicyConfig = new CrossOriginEmbedderPolicyConfig();
                  crossOriginEmbedderPolicyConfig.setCrossOriginEmbedderPolicy(
                      CrossOriginEmbedderPolicy.UNSAFE_NONE);
                  headersCfg.setCrossOriginEmbedderPolicyConfig(crossOriginEmbedderPolicyConfig);

                  final var crossOriginResourcePolicyConfig = new CrossOriginResourcePolicyConfig();
                  crossOriginResourcePolicyConfig.setCrossOriginResourcePolicy(
                      CrossOriginResourcePolicy.CROSS_ORIGIN);
                  headersCfg.setCrossOriginResourcePolicyConfig(crossOriginResourcePolicyConfig);

                  props.setSecurityHeaders(headersCfg);
                });

    @UserDefinition private static final User USER = new User(USERNAME, PASSWORD, List.of());
    private static CamundaClient camundaClient;

    @Test
    void testModifiedHeaders(@Authenticated(USERNAME) final CamundaClient userClient)
        throws Exception {
      // when
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(createUri(camundaClient, URL_TO_VISIT))
              .header(HttpHeaders.AUTHORIZATION, basicAuthentication())
              .build();
      final HttpResponse<String> response =
          HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

      // then
      assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK);

      final var headers = response.headers().map();
      assertThat(headers).doesNotContainKey(X_CONTENT_TYPE_OPTIONS);
      assertThat(headers).containsEntry(X_XSS_PROTECTION, List.of("0"));
      assertThat(headers).doesNotContainKey(CACHE_CONTROL);
      assertThat(headers).doesNotContainKey(PRAGMA);
      assertThat(headers).doesNotContainKey(EXPIRES);
      assertThat(headers).doesNotContainKey(X_FRAME_OPTIONS);
      assertThat(headers).containsEntry(CONTENT_SECURITY_POLICY, List.of("self; camunda.com"));
      assertThat(headers).containsEntry(REFERRER_POLICY, List.of("no-referrer"));
      assertThat(headers).containsEntry(PERMISSIONS_POLICY, List.of("camera=*"));
      assertThat(headers).containsEntry(CROSS_ORIGIN_OPENER_POLICY, List.of("unsafe-none"));
      assertThat(headers).containsEntry(CROSS_ORIGIN_EMBEDDER_POLICY, List.of("unsafe-none"));
      assertThat(headers).containsEntry(CROSS_ORIGIN_RESOURCE_POLICY, List.of("cross-origin"));
    }
  }
}
