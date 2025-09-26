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
import io.camunda.security.configuration.headers.ContentSecurityPolicyConfig;
import io.camunda.security.configuration.headers.PermissionsPolicyConfig;
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
public class SecurityHeadersBasicAuthIT extends SecurityHeadersBaseIT {

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  private static AuthorizationsUtil authUtil;
  @AutoClose private static CamundaClient camundaClient;

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  @BeforeEach
  void beforeEach() {
    broker
        .withCamundaExporter("http://" + CONTAINER.getHttpHostAddress())
        .withProperty(
            "camunda.data.secondary-storage.elasticsearch.url", CONTAINER.getHttpHostAddress());
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

  private static String basicAuthentication() {
    return "Basic " + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
  }
}
