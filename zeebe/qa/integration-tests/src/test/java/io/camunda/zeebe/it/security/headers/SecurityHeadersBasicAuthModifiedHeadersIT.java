/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.security.headers;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY_REPORT_ONLY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_OPENER_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY;
import static com.google.common.net.HttpHeaders.EXPIRES;
import static com.google.common.net.HttpHeaders.PERMISSIONS_POLICY;
import static com.google.common.net.HttpHeaders.PRAGMA;
import static com.google.common.net.HttpHeaders.REFERRER_POLICY;
import static com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS;
import static com.google.common.net.HttpHeaders.X_FRAME_OPTIONS;
import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
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
import io.camunda.security.configuration.secureheaders.values.CrossOriginEmbedderPolicy;
import io.camunda.security.configuration.secureheaders.values.CrossOriginOpenerPolicy;
import io.camunda.security.configuration.secureheaders.values.CrossOriginResourcePolicy;
import io.camunda.security.configuration.secureheaders.values.ReferrerPolicy;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;

@ZeebeIntegration
public class SecurityHeadersBasicAuthModifiedHeadersIT extends SecurityHeadersBaseIT {

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  private static AuthorizationsUtil authUtil;
  @AutoClose private static CamundaClient camundaClient;

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC)
          .withSecurityConfig(
              props -> {
                final var headersCfg = new SecurityHeaderConfigurations();

                final var contentTypeOptionsCfg = new ContentTypeOptionsConfig();
                contentTypeOptionsCfg.setEnabled(false);
                headersCfg.setContentTypeOptionsConfig(contentTypeOptionsCfg);

                final var cacheControlCfg = new CacheControlConfig();
                cacheControlCfg.setEnabled(false);
                headersCfg.setCacheConfig(cacheControlCfg);

                final var frameOptionsCfg = new FrameOptionsConfig();
                frameOptionsCfg.setEnabled(false);
                headersCfg.setFrameOptionsConfig(frameOptionsCfg);

                final var contentSecurityPolicyConfig = new ContentSecurityPolicyConfig();
                contentSecurityPolicyConfig.setEnabled(true);
                contentSecurityPolicyConfig.setReportOnly(true);
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

  @BeforeEach
  void beforeEach() {
    broker.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    broker.start();

    camundaClient = createClient(broker, USERNAME, PASSWORD);
    authUtil = new AuthorizationsUtil(broker, camundaClient, CONTAINER.getHttpHostAddress());
    authUtil.awaitUserExistsInElasticsearch(USERNAME);
  }

  @Override
  protected CamundaClient getCamundaClient() {
    return camundaClient;
  }

  @Override
  protected HttpResponse<String> makeAuthenticatedRequest(final String path) throws Exception {
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(createUri(camundaClient, path))
            .header(HttpHeaders.AUTHORIZATION, basicAuthentication())
            .build();
    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Override
  protected void assertSecurityHeaders(final Map<String, List<String>> headers) {
    assertThat(headers).doesNotContainKey(X_CONTENT_TYPE_OPTIONS);
    assertThat(headers).doesNotContainKey(CACHE_CONTROL);
    assertThat(headers).doesNotContainKey(PRAGMA);
    assertThat(headers).doesNotContainKey(EXPIRES);
    assertThat(headers).doesNotContainKey(X_FRAME_OPTIONS);
    assertThat(headers).doesNotContainKey(CONTENT_SECURITY_POLICY);
    assertThat(headers)
        .containsEntry(CONTENT_SECURITY_POLICY_REPORT_ONLY, List.of("self; camunda.com"));
    assertThat(headers).containsEntry(REFERRER_POLICY, List.of("no-referrer"));
    assertThat(headers).containsEntry(PERMISSIONS_POLICY, List.of("camera=*"));
    assertThat(headers).containsEntry(CROSS_ORIGIN_OPENER_POLICY, List.of("unsafe-none"));
    assertThat(headers).containsEntry(CROSS_ORIGIN_EMBEDDER_POLICY, List.of("unsafe-none"));
    assertThat(headers).containsEntry(CROSS_ORIGIN_RESOURCE_POLICY, List.of("cross-origin"));
  }

  private static String basicAuthentication() {
    return "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
  }
}
