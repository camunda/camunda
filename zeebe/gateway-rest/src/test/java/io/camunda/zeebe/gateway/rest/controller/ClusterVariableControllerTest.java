/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.ClusterVariableServices;
import io.camunda.service.ClusterVariableServices.ClusterVariableRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = ClusterVariableController.class)
public class ClusterVariableControllerTest extends RestControllerTest {

  static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);
  static final String CLUSTER_VARIABLE_PREFIX_URL = "/v2/cluster-variables";
  static final String GLOBAL_URL = CLUSTER_VARIABLE_PREFIX_URL + "/global";
  static final String GLOBAL_WITH_NAME_URL = GLOBAL_URL + "/%s";
  static final String TENANT_URL = CLUSTER_VARIABLE_PREFIX_URL + "/tenants/%s";
  static final String TENANT_WITH_NAME_URL = TENANT_URL + "/%s";

  @Captor ArgumentCaptor<ClusterVariableRequest> createRequestCaptor;
  @MockitoBean ClusterVariableServices clusterVariableServices;
  @MockitoBean SecurityConfiguration securityConfiguration;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(clusterVariableServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(clusterVariableServices);
    when(securityConfiguration.getCompiledIdValidationPattern()).thenReturn(ID_PATTERN);
  }

  @Test
  void shouldGetGlobalClusterVariable() {
    // given
    final var clusterVariableEntity = mock(ClusterVariableEntity.class);
    when(clusterVariableEntity.name()).thenReturn("name");
    when(clusterVariableEntity.value()).thenReturn("value");
    when(clusterVariableEntity.isPreview()).thenReturn(false);
    when(clusterVariableEntity.scope()).thenReturn(ClusterVariableScope.GLOBAL);

    when(clusterVariableServices.getGloballyScopedClusterVariable(
            any(ClusterVariableRequest.class)))
        .thenReturn(clusterVariableEntity);

    // when / then
    webClient
        .get()
        .uri(GLOBAL_WITH_NAME_URL.formatted("foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices).getGloballyScopedClusterVariable(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isNull();
    assertThat(capturedRequest.tenantId()).isNull();
  }

  @Test
  void shouldRejectGetGlobalClusterVariableWithInvalidName() {
    // given
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided name contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_WITH_NAME_URL.formatted("$foo"));

    // when / then
    webClient
        .get()
        .uri(GLOBAL_WITH_NAME_URL.formatted("$foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldCreateGlobalClusterVariable() {
    // given
    when(clusterVariableServices.createGloballyScopedClusterVariable(
            any(ClusterVariableRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ClusterVariableRecord().setName("foo")));

    final var request =
        """
        {
            "name": "foo",
            "value": "bar"
        }""";

    // when / then
    webClient
        .post()
        .uri(GLOBAL_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices)
        .createGloballyScopedClusterVariable(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isEqualTo("bar");
    assertThat(capturedRequest.tenantId()).isNull();
  }

  @Test
  void shouldRejectCreationWithMissingGlobalClusterVariableName() {
    // given
    final var request =
        """
        {
            "value": "bar"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "No name provided.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_URL);

    // when / then
    webClient
        .post()
        .uri(GLOBAL_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectCreationWithInvalidGlobalClusterVariableName() {
    // given
    final var request =
        """
        {
            "name": "$foo",
            "value": "bar"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided name contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_URL);

    // when / then
    webClient
        .post()
        .uri(GLOBAL_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectCreationWithoutGlobalClusterVariableValue() {
    // given
    final var request =
        """
        {
            "name": "foo"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "No value provided.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_URL);

    // when / then
    webClient
        .post()
        .uri(GLOBAL_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldDeleteGlobalClusterVariable() {
    // given
    when(clusterVariableServices.deleteGloballyScopedClusterVariable(
            any(ClusterVariableRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ClusterVariableRecord().setName("foo")));

    // when / then
    webClient
        .delete()
        .uri(GLOBAL_WITH_NAME_URL.formatted("foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clusterVariableServices)
        .deleteGloballyScopedClusterVariable(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isNull();
    assertThat(capturedRequest.tenantId()).isNull();
  }

  @Test
  void shouldUpdateGlobalClusterVariable() {
    // given
    when(clusterVariableServices.updateGloballyScopedClusterVariable(
            any(ClusterVariableRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ClusterVariableRecord().setName("foo")));

    final var request =
        """
        {
            "value": "newValue"
        }""";

    // when / then
    webClient
        .put()
        .uri(GLOBAL_WITH_NAME_URL.formatted("foo"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices)
        .updateGloballyScopedClusterVariable(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isEqualTo("newValue");
    assertThat(capturedRequest.tenantId()).isNull();
  }

  @Test
  void shouldRejectUpdateWithInvalidGlobalClusterVariableName() {
    // given
    final var request =
        """
        {
            "value": "newValue"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided name contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_WITH_NAME_URL.formatted("$foo"));

    // when / then
    webClient
        .put()
        .uri(GLOBAL_WITH_NAME_URL.formatted("$foo"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectUpdateWithoutGlobalClusterVariableValue() {
    // given
    final var request = "{}";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "No value provided.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_WITH_NAME_URL.formatted("foo"));

    // when / then
    webClient
        .put()
        .uri(GLOBAL_WITH_NAME_URL.formatted("foo"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectDeletionWithInvalidGlobalClusterVariableName() {
    // given
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided name contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_WITH_NAME_URL.formatted("$foo"));

    // when / then
    webClient
        .delete()
        .uri(GLOBAL_WITH_NAME_URL.formatted("$foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldGetTenantClusterVariable() {
    // given
    final var clusterVariableEntity = mock(ClusterVariableEntity.class);
    when(clusterVariableEntity.name()).thenReturn("name");
    when(clusterVariableEntity.value()).thenReturn("value");
    when(clusterVariableEntity.isPreview()).thenReturn(false);
    when(clusterVariableEntity.scope()).thenReturn(ClusterVariableScope.TENANT);
    when(clusterVariableEntity.tenantId()).thenReturn("tenant1");

    when(clusterVariableServices.getTenantScopedClusterVariable(any(ClusterVariableRequest.class)))
        .thenReturn(clusterVariableEntity);

    // when / then
    webClient
        .get()
        .uri(TENANT_WITH_NAME_URL.formatted("tenant1", "foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices).getTenantScopedClusterVariable(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isNull();
    assertThat(capturedRequest.tenantId()).isEqualTo("tenant1");
  }

  @Test
  void shouldRejectGetTenantClusterVariableWithInvalidName() {
    // given
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided name contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(TENANT_WITH_NAME_URL.formatted("tenant1", "$foo"));

    // when / then
    webClient
        .get()
        .uri(TENANT_WITH_NAME_URL.formatted("tenant1", "$foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectGetTenantClusterVariableWithInvalidTenantId() {
    // given
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '^[\\\\w\\\\.-]{1,31}$'.",
              "instance": "%s"
            }"""
            .formatted(TENANT_WITH_NAME_URL.formatted("$tenant1", "foo"));

    // when / then
    webClient
        .get()
        .uri(TENANT_WITH_NAME_URL.formatted("$tenant1", "foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldCreateTenantClusterVariable() {
    // given
    when(clusterVariableServices.createTenantScopedClusterVariable(
            any(ClusterVariableRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ClusterVariableRecord().setName("foo")));

    final var request =
        """
        {
            "name": "foo",
            "value": "bar"
        }""";

    // when / then
    webClient
        .post()
        .uri(TENANT_URL.formatted("tenant1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices)
        .createTenantScopedClusterVariable(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isEqualTo("bar");
    assertThat(capturedRequest.tenantId()).isEqualTo("tenant1");
  }

  @Test
  void shouldRejectCreationWithMissingTenantClusterVariableName() {
    // given
    final var request =
        """
        {
            "value": "bar"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "No name provided.",
              "instance": "%s"
            }"""
            .formatted(TENANT_URL.formatted("tenant1"));

    // when / then
    webClient
        .post()
        .uri(TENANT_URL.formatted("tenant1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectCreationWithInvalidTenantClusterVariableName() {
    // given
    final var request =
        """
        {
            "name": "$foo",
            "value": "bar"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided name contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(TENANT_URL.formatted("tenant1"));

    // when / then
    webClient
        .post()
        .uri(TENANT_URL.formatted("tenant1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectCreationWithInvalidTenantId() {
    // given
    final var request =
        """
        {
            "name": "foo",
            "value": "bar"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '^[\\\\w\\\\.-]{1,31}$'.",
              "instance": "%s"
            }"""
            .formatted(TENANT_URL.formatted("$tenant1"));

    // when / then
    webClient
        .post()
        .uri(TENANT_URL.formatted("$tenant1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectCreationWithoutValue() {
    // given
    final var request =
        """
        {
            "name": "foo"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "No value provided.",
              "instance": "%s"
            }"""
            .formatted(TENANT_URL.formatted("tenant1"));

    // when / then
    webClient
        .post()
        .uri(TENANT_URL.formatted("tenant1"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldDeleteTenantClusterVariable() {
    // given
    when(clusterVariableServices.deleteTenantScopedClusterVariable(
            any(ClusterVariableRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ClusterVariableRecord().setName("foo")));

    // when / then
    webClient
        .delete()
        .uri(TENANT_WITH_NAME_URL.formatted("tenant1", "foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(clusterVariableServices)
        .deleteTenantScopedClusterVariable(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isNull();
    assertThat(capturedRequest.tenantId()).isEqualTo("tenant1");
  }

  @Test
  void shouldUpdateTenantClusterVariable() {
    // given
    when(clusterVariableServices.updateTenantScopedClusterVariable(
            any(ClusterVariableRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new ClusterVariableRecord().setName("foo")));

    final var request =
        """
        {
            "value": "newValue"
        }""";

    // when / then
    webClient
        .put()
        .uri(TENANT_WITH_NAME_URL.formatted("tenant1", "foo"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices)
        .updateTenantScopedClusterVariable(createRequestCaptor.capture());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isEqualTo("newValue");
    assertThat(capturedRequest.tenantId()).isEqualTo("tenant1");
  }

  @Test
  void shouldRejectUpdateWithInvalidTenantClusterVariableName() {
    // given
    final var request =
        """
        {
            "value": "newValue"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided name contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(TENANT_WITH_NAME_URL.formatted("tenant1", "$foo"));

    // when / then
    webClient
        .put()
        .uri(TENANT_WITH_NAME_URL.formatted("tenant1", "$foo"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectUpdateWithInvalidTenantId() {
    // given
    final var request =
        """
        {
            "value": "newValue"
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '^[\\\\w\\\\.-]{1,31}$'.",
              "instance": "%s"
            }"""
            .formatted(TENANT_WITH_NAME_URL.formatted("$tenant1", "foo"));

    // when / then
    webClient
        .put()
        .uri(TENANT_WITH_NAME_URL.formatted("$tenant1", "foo"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectUpdateWithoutTenantClusterVariableValue() {
    // given
    final var request = "{}";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "No value provided.",
              "instance": "%s"
            }"""
            .formatted(TENANT_WITH_NAME_URL.formatted("tenant1", "foo"));

    // when / then
    webClient
        .put()
        .uri(TENANT_WITH_NAME_URL.formatted("tenant1", "foo"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectDeletionWithInvalidTenantId() {
    // given
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '^[\\\\w\\\\.-]{1,31}$'.",
              "instance": "%s"
            }"""
            .formatted(TENANT_WITH_NAME_URL.formatted("$tenant", "foo"));

    // when / then
    webClient
        .delete()
        .uri(TENANT_WITH_NAME_URL.formatted("$tenant", "foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectDeletionWithInvalidName() {
    // given
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided name contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(TENANT_WITH_NAME_URL.formatted("tenant", "$foo"));

    // when / then
    webClient
        .delete()
        .uri(TENANT_WITH_NAME_URL.formatted("tenant", "$foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }
}
