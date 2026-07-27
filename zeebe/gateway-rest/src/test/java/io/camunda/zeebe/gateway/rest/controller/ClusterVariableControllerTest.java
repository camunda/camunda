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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.ClusterVariableKind;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.ClusterVariableServices;
import io.camunda.service.ClusterVariableServices.ClusterVariableRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = ClusterVariableController.class)
public class ClusterVariableControllerTest extends RestControllerTest {

  static final Pattern ID_PATTERN =
      Pattern.compile(CamundaSecurityLibraryProperties.DEFAULT_ID_REGEX);
  static final String CLUSTER_VARIABLE_PREFIX_URL = "/v2/cluster-variables";
  static final String GLOBAL_URL = CLUSTER_VARIABLE_PREFIX_URL + "/global";
  static final String GLOBAL_WITH_NAME_URL = GLOBAL_URL + "/%s";
  static final String TENANT_URL = CLUSTER_VARIABLE_PREFIX_URL + "/tenants/%s";
  static final String TENANT_WITH_NAME_URL = TENANT_URL + "/%s";
  static final String SEARCH_URL = CLUSTER_VARIABLE_PREFIX_URL + "/search";
  // large enough that 101 minimal metadata entries (~1.8k serialized chars) don't also trip
  // this limit, so the too-many-entries test exercises only the entry-count check
  static final int TEST_MAX_CLUSTER_VARIABLE_METADATA_SIZE = 2000;

  @Captor ArgumentCaptor<ClusterVariableRequest> createRequestCaptor;
  @Captor ArgumentCaptor<ClusterVariableQuery> searchQueryCaptor;
  @MockitoBean ClusterVariableServices clusterVariableServices;
  @MockitoBean CamundaSecurityLibraryProperties cslProperties;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setupServices() {
    lenient()
        .when(serviceRegistry.clusterVariableServices(any()))
        .thenReturn(clusterVariableServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(cslProperties.getCompiledIdValidationPattern()).thenReturn(ID_PATTERN);
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
            any(ClusterVariableRequest.class), any()))
        .thenReturn(clusterVariableEntity);

    // when / then
    webClient
        .get()
        .uri(GLOBAL_WITH_NAME_URL.formatted("foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices)
        .getGloballyScopedClusterVariable(createRequestCaptor.capture(), any());
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
    final var metadata = Map.<String, Object>of("kind", "CREDENTIAL", "schemaVersion", 2);
    when(clusterVariableServices.createGloballyScopedClusterVariable(
            any(ClusterVariableRequest.class), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ClusterVariableRecord()
                    .setName("foo")
                    .setMetadata(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(metadata)))));

    final var request =
        """
        {
            "name": "foo",
            "value": "bar",
            "metadata": {
                "kind": "CREDENTIAL",
                "schemaVersion": 2
            }
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
        .isOk()
        .expectBody()
        .jsonPath("$.metadata.kind")
        .isEqualTo("CREDENTIAL")
        .jsonPath("$.metadata.schemaVersion")
        .isEqualTo(2);

    verify(clusterVariableServices)
        .createGloballyScopedClusterVariable(createRequestCaptor.capture(), any());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isEqualTo("bar");
    assertThat(capturedRequest.tenantId()).isNull();
    assertThat(capturedRequest.metadata()).containsExactlyInAnyOrderEntriesOf(metadata);
  }

  @Test
  void shouldRejectCreationWithNonScalarMetadataValue() {
    // given
    final var request =
        """
        {
            "name": "foo",
            "value": "bar",
            "metadata": {
                "kind": ["CREDENTIAL"]
            }
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The metadata value for key 'kind' is of type 'ArrayList' but must be a string or a number.",
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
  void shouldRejectCreationWithTooManyMetadataEntries() {
    // given
    final var metadataEntries =
        IntStream.range(0, 101)
            .mapToObj(i -> "\"%d\": \"%d\"".formatted(i, i))
            .collect(Collectors.joining(","));
    final var request =
        """
        {
            "name": "foo",
            "value": "bar",
            "metadata": { %s }
        }"""
            .formatted(metadataEntries);

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
        .jsonPath("$.detail")
        .isEqualTo("The provided metadata has 101 entries but must not exceed 100 entries.");
  }

  @Test
  void shouldRejectCreationWithOversizedMetadata() {
    // given: exceeds the test-configured metadata size limit (see TestConfig) without needing a
    // huge payload
    final var largeValue = "x".repeat(TEST_MAX_CLUSTER_VARIABLE_METADATA_SIZE);
    final var request =
        """
        {
            "name": "foo",
            "value": "bar",
            "metadata": { "key": "%s" }
        }"""
            .formatted(largeValue);

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
        .jsonPath("$.detail")
        .isEqualTo(
            "The provided metadata exceeds the maximum serialized size of %d bytes."
                .formatted(TEST_MAX_CLUSTER_VARIABLE_METADATA_SIZE));
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
            any(ClusterVariableRequest.class), any()))
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
        .deleteGloballyScopedClusterVariable(createRequestCaptor.capture(), any());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isNull();
    assertThat(capturedRequest.tenantId()).isNull();
  }

  @Test
  void shouldUpdateGlobalClusterVariable() {
    // given
    when(clusterVariableServices.updateGloballyScopedClusterVariable(
            any(ClusterVariableRequest.class), any()))
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
        .updateGloballyScopedClusterVariable(createRequestCaptor.capture(), any());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isEqualTo("newValue");
    assertThat(capturedRequest.tenantId()).isNull();
    assertThat(capturedRequest.metadata()).isEmpty();
  }

  @Test
  void shouldRejectUpdateWithNonScalarMetadataValue() {
    // given
    final var request =
        """
        {
            "value": "newValue",
            "metadata": {
                "kind": ["CREDENTIAL"]
            }
        }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The metadata value for key 'kind' is of type 'ArrayList' but must be a string or a number.",
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
  void shouldRejectUpdateWithTooManyMetadataEntries() {
    // given
    final var metadataEntries =
        IntStream.range(0, 101)
            .mapToObj(i -> "\"%d\": \"%d\"".formatted(i, i))
            .collect(Collectors.joining(","));
    final var request =
        """
        {
            "value": "newValue",
            "metadata": { %s }
        }"""
            .formatted(metadataEntries);

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
        .jsonPath("$.detail")
        .isEqualTo("The provided metadata has 101 entries but must not exceed 100 entries.");
  }

  @Test
  void shouldRejectUpdateWithOversizedMetadata() {
    // given: exceeds the test-configured metadata size limit (see TestConfig) without needing a
    // huge payload
    final var largeValue = "x".repeat(TEST_MAX_CLUSTER_VARIABLE_METADATA_SIZE);
    final var request =
        """
        {
            "value": "newValue",
            "metadata": { "key": "%s" }
        }"""
            .formatted(largeValue);

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
        .jsonPath("$.detail")
        .isEqualTo(
            "The provided metadata exceeds the maximum serialized size of %d bytes."
                .formatted(TEST_MAX_CLUSTER_VARIABLE_METADATA_SIZE));
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

    when(clusterVariableServices.getTenantScopedClusterVariable(
            any(ClusterVariableRequest.class), any()))
        .thenReturn(clusterVariableEntity);

    // when / then
    webClient
        .get()
        .uri(TENANT_WITH_NAME_URL.formatted("tenant1", "foo"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices)
        .getTenantScopedClusterVariable(createRequestCaptor.capture(), any());
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
            any(ClusterVariableRequest.class), any()))
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
        .createTenantScopedClusterVariable(createRequestCaptor.capture(), any());
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
            any(ClusterVariableRequest.class), any()))
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
        .deleteTenantScopedClusterVariable(createRequestCaptor.capture(), any());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isNull();
    assertThat(capturedRequest.tenantId()).isEqualTo("tenant1");
  }

  @Test
  void shouldUpdateTenantClusterVariable() {
    // given
    when(clusterVariableServices.updateTenantScopedClusterVariable(
            any(ClusterVariableRequest.class), any()))
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
        .updateTenantScopedClusterVariable(createRequestCaptor.capture(), any());
    final var capturedRequest = createRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("foo");
    assertThat(capturedRequest.value()).isEqualTo("newValue");
    assertThat(capturedRequest.tenantId()).isEqualTo("tenant1");
    assertThat(capturedRequest.metadata()).isEmpty();
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

  @Test
  void shouldCreateGlobalClusterVariableWithSecretReferenceKind() {
    // given
    final var record =
        new ClusterVariableRecord()
            .setName("myVar")
            .setScope(io.camunda.zeebe.protocol.record.value.ClusterVariableScope.GLOBAL)
            .setKind(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.SECRET_REFERENCE);
    when(clusterVariableServices.createGloballyScopedClusterVariable(
            createRequestCaptor.capture(), any()))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when / then
    webClient
        .post()
        .uri(GLOBAL_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"name\":\"myVar\",\"value\":\"camunda.secrets.MY_SECRET\",\"kind\":\"SECRET_REFERENCE\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json("{\"kind\":\"SECRET_REFERENCE\"}", JsonCompareMode.LENIENT);

    assertThat(createRequestCaptor.getValue().kind())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.SECRET_REFERENCE);
  }

  @Test
  void shouldCreateGlobalClusterVariableWithDefaultJsonKind() {
    // given
    final var record =
        new ClusterVariableRecord()
            .setName("myVar")
            .setScope(io.camunda.zeebe.protocol.record.value.ClusterVariableScope.GLOBAL)
            .setKind(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.JSON);
    when(clusterVariableServices.createGloballyScopedClusterVariable(
            createRequestCaptor.capture(), any()))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when / then
    webClient
        .post()
        .uri(GLOBAL_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"myVar\",\"value\":\"val\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json("{\"kind\":\"JSON\"}", JsonCompareMode.LENIENT);

    assertThat(createRequestCaptor.getValue().kind()).isNull();
  }

  @Test
  void shouldSearchClusterVariablesByKindFilter() {
    // given
    when(clusterVariableServices.search(any(), any()))
        .thenReturn(
            new SearchQueryResult.Builder<ClusterVariableEntity>()
                .total(0)
                .items(List.of())
                .build());

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk();

    verify(clusterVariableServices).search(searchQueryCaptor.capture(), any());
    final var capturedQuery = searchQueryCaptor.getValue();
    assertThat(capturedQuery).isNotNull();
  }

  @Test
  void shouldReturnKindInClusterVariableSearchResults() {
    // given
    final var entity = mock(ClusterVariableEntity.class);
    when(entity.name()).thenReturn("myVar");
    when(entity.value()).thenReturn("\"val\"");
    when(entity.isPreview()).thenReturn(false);
    when(entity.scope()).thenReturn(ClusterVariableScope.GLOBAL);
    when(entity.kind()).thenReturn(ClusterVariableKind.SECRET_REFERENCE);
    when(entity.metadata()).thenReturn(List.of());
    when(clusterVariableServices.search(any(), any()))
        .thenReturn(
            new SearchQueryResult.Builder<ClusterVariableEntity>()
                .total(1)
                .items(List.of(entity))
                .build());

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items[0].kind")
        .isEqualTo("SECRET_REFERENCE");
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    GatewayRestConfiguration gatewayRestConfiguration() {
      final var config = new GatewayRestConfiguration();
      // kept small so oversized-metadata tests don't need huge payloads
      config.setMaxClusterVariableMetadataSize(TEST_MAX_CLUSTER_VARIABLE_METADATA_SIZE);
      return config;
    }
  }
}
