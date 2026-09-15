/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.ServiceOptions;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient.ListSecretsPagedResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GcpSecretManagerSecretStoreTest {

  private static final String PROJECT = "my-project";

  @Mock private SecretManagerServiceClient client;

  @Test
  void shouldResolveKnownSecret() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "camunda-");
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("s3cr3t"));

    // when
    final var result = store.resolve(Set.of("db-password"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
  }

  @Test
  void shouldPrependPathPrefixToSecretId() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "camunda-");
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("v"));

    // when
    store.resolve(Set.of("token"));

    // then
    final var captor = ArgumentCaptor.forClass(SecretVersionName.class);
    verify(client).accessSecretVersion(captor.capture());
    assertThat(captor.getValue().getSecret()).isEqualTo("camunda-token");
    assertThat(captor.getValue().getProject()).isEqualTo(PROJECT);
  }

  @Test
  void shouldUseBareNameWhenPrefixIsNull() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, null);
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("v"));

    // when
    store.resolve(Set.of("token"));

    // then
    final var captor = ArgumentCaptor.forClass(SecretVersionName.class);
    verify(client).accessSecretVersion(captor.capture());
    assertThat(captor.getValue().getSecret()).isEqualTo("token");
  }

  @Test
  void shouldResolveOneNamePerCallByDefault() {
    // given a store with no container secret id, so it resolves via one accessSecretVersion call
    // per reference
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "camunda-");

    // then
    assertThat(store.namesPerCall()).isEqualTo(1);
  }

  @Test
  void shouldNotBeConcurrencyEligibleWithContainerSecret() {
    // given a container secret fetches every reference in a single accessSecretVersion call, so
    // chunking it across a thread pool would only add calls, not remove round trips
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "camunda-", "app-config");

    // then
    assertThat(store.namesPerCall()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void shouldReturnNotFoundForMissingSecret() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenThrow(apiException(Code.NOT_FOUND));

    // when
    final var result = store.resolve(Set.of("missing"));

    // then
    assertThat(((Failed) result.get("missing")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnInvalidRefForInvalidArgument() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenThrow(apiException(Code.INVALID_ARGUMENT));

    // when
    final var result = store.resolve(Set.of("bad"));

    // then
    assertThat(((Failed) result.get("bad")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnAccessDeniedForPermissionDenied() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenThrow(apiException(Code.PERMISSION_DENIED));

    // when
    final var result = store.resolve(Set.of("secret"));

    // then
    assertThat(((Failed) result.get("secret")).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldThrowUnavailableOnConnectivityError() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenThrow(apiException(Code.UNAVAILABLE));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("any")))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldReturnResultForEveryRefInBatch() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenAnswer(
            invocation -> {
              final SecretVersionName name = invocation.getArgument(0);
              if (name.getSecret().equals("known")) {
                return response("value");
              }
              throw apiException(Code.NOT_FOUND);
            });

    // when
    final var result = store.resolve(Set.of("known", "missing"));

    // then
    assertThat(result).containsKeys("known", "missing");
    assertThat(result.get("known")).isInstanceOf(Resolved.class);
    assertThat(result.get("missing")).isInstanceOf(Failed.class);
  }

  @Test
  void shouldReturnEmptyMapForEmptyRefs() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "");

    // when
    final var result = store.resolve(Set.of());

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(client);
  }

  @Test
  void shouldListSecretsFilteredByPrefixAndStripIt() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "camunda-");
    mockListSecrets("camunda-db", "camunda-api", "other-ignored");

    // when
    final var refs = store.list();

    // then — only prefixed secrets, with the prefix stripped
    assertThat(refs).containsExactlyInAnyOrder("db", "api");
  }

  @Test
  void shouldSkipSecretWhoseIdEqualsPrefixWhenListing() {
    // given — a secret named exactly like the prefix yields an empty logical name
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "camunda-");
    mockListSecrets("camunda-db", "camunda-");

    // when
    final var refs = store.list();

    // then — the prefix-only secret is skipped, not returned as an empty ref
    assertThat(refs).containsExactly("db");
  }

  @Test
  void shouldThrowUnavailableWhenListFails() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "");
    when(client.listSecrets(any(ProjectName.class))).thenThrow(apiException(Code.UNAVAILABLE));

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldNotLeakSecretValueInResolvedToString() {
    // given
    final var resolved = new Resolved("super-secret");

    // when / then — value must be masked in toString
    assertThat(resolved.toString()).doesNotContain("super-secret");
  }

  // ---- JSON container mode ----

  @Test
  void shouldResolveMultipleKeysFromOneContainerFetch() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "camunda-", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(response("{\"DB_PASSWORD\":\"s3cr3t\",\"API_KEY\":\"k3y\"}"));

    // when
    final var result = store.resolve(Set.of("DB_PASSWORD", "API_KEY"));

    // then — only one access call for both keys, at the prefixed container id
    assertThat(result.get("DB_PASSWORD"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("s3cr3t");
    assertThat(result.get("API_KEY"))
        .isInstanceOf(Resolved.class)
        .extracting(r -> ((Resolved) r).value())
        .isEqualTo("k3y");
    final var captor = ArgumentCaptor.forClass(SecretVersionName.class);
    verify(client, times(1)).accessSecretVersion(captor.capture());
    assertThat(captor.getValue().getSecret()).isEqualTo("camunda-app-config");
  }

  @Test
  void shouldReturnNotFoundForMissingKeyInContainer() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(response("{\"OTHER\":\"v\"}"));

    // when
    final var result = store.resolve(Set.of("MISSING"));

    // then
    assertThat(((Failed) result.get("MISSING")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnNotFoundForNullValuedKeyInContainer() {
    // given — a key present in the container but with a JSON null value
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(response("{\"NULLED\":null}"));

    // when
    final var result = store.resolve(Set.of("NULLED"));

    // then — a null value is treated as no value, not as a resolvable secret
    assertThat(((Failed) result.get("NULLED")).code()).isEqualTo(SecretErrorCode.NOT_FOUND);
  }

  @Test
  void shouldReturnInvalidRefForNonStringKeyInContainer() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(response("{\"NUM\":42}"));

    // when
    final var result = store.resolve(Set.of("NUM"));

    // then
    assertThat(((Failed) result.get("NUM")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForAllKeysWhenContainerIsNotValidJson() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("not json"));

    // when
    final var result = store.resolve(Set.of("A", "B"));

    // then — the shared parse failure applies to every requested key
    assertThat(((Failed) result.get("A")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
    assertThat(((Failed) result.get("B")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldReturnInvalidRefForAllKeysWhenContainerIsNotJsonObject() {
    // given — valid JSON, but an array rather than an object
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(response("[\"a\",\"b\"]"));

    // when
    final var result = store.resolve(Set.of("A"));

    // then
    assertThat(((Failed) result.get("A")).code()).isEqualTo(SecretErrorCode.INVALID_REF);
  }

  @Test
  void shouldListKeysFromContainer() {
    // given
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(response("{\"DB_PASSWORD\":\"s3cr3t\",\"API_KEY\":\"k3y\"}"));

    // when
    final var refs = store.list();

    // then
    assertThat(refs).containsExactlyInAnyOrder("DB_PASSWORD", "API_KEY");
  }

  @Test
  void shouldListNullValuedKeyFromContainer() {
    // given — list reports every top-level field name, including one whose value is JSON null
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenReturn(response("{\"API_KEY\":\"k3y\",\"NULLED\":null}"));

    // when
    final var refs = store.list();

    // then — the null-valued key is listed even though resolve() would report it NOT_FOUND
    assertThat(refs).containsExactlyInAnyOrder("API_KEY", "NULLED");
  }

  @Test
  void shouldThrowUnavailableWhenContainerListIsNotJsonObject() {
    // given — valid JSON, but not an object: list() cannot enumerate keys
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("[\"a\"]"));

    // when / then — the malformed-content message must surface, not a generic "unavailable" one
    assertThatThrownBy(store::list)
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessageContaining("is not a JSON object");
  }

  @Test
  void shouldNotCallListSecretsWhenContainerModeEnabled() {
    // given — container mode must never fall back to the flat listSecrets scan
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("{}"));

    // when
    store.list();

    // then
    verify(client, times(0)).listSecrets(any(ProjectName.class));
  }

  @Test
  void shouldUseOneByOneModeWhenContainerSecretIdIsBlank() {
    // given — a blank container id must not enable container mode
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", " ");
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("v"));

    // when
    store.resolve(Set.of("token"));

    // then — resolves the reference as its own secret, not a container key
    final var captor = ArgumentCaptor.forClass(SecretVersionName.class);
    verify(client).accessSecretVersion(captor.capture());
    assertThat(captor.getValue().getSecret()).isEqualTo("token");
  }

  @Test
  void shouldFailAllKeysWithClassifiedCodeWhenContainerFetchThrowsApiException() {
    // given — container mode; the access call itself fails with an API error
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenThrow(apiException(Code.PERMISSION_DENIED));

    // when
    final var result = store.resolve(Set.of("A", "B"));

    // then — every requested key fails with the classified code
    assertThat(((Failed) result.get("A")).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
    assertThat(((Failed) result.get("B")).code()).isEqualTo(SecretErrorCode.ACCESS_DENIED);
  }

  @Test
  void shouldThrowUnavailableWhenContainerResolveFetchThrowsRuntimeException() {
    // given — container mode; a non-API runtime failure during resolve is store-wide
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenThrow(new RuntimeException("boom"));

    // when / then
    assertThatThrownBy(() -> store.resolve(Set.of("A")))
        .isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldThrowUnavailableWhenContainerListIsNotValidJson() {
    // given — container mode; list() over a syntactically invalid container
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("not json"));

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
  }

  @Test
  void shouldThrowUnavailableWhenContainerListFetchThrowsRuntimeException() {
    // given — container mode; a non-API runtime failure during list is store-wide
    final var store = new GcpSecretManagerSecretStore(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class)))
        .thenThrow(new RuntimeException("boom"));

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
  }

  // ---- optional project id ----

  @Test
  void shouldConstructStoreWithNullProjectId() {
    // given / when — a null project id is allowed; it is resolved from the environment at build
    // time and only matters once a real GCP call is made
    final var store = new GcpSecretManagerSecretStore(client, null, "camunda-");

    // then — construction succeeds without touching GCP
    assertThat(store).isNotNull();
    verifyNoInteractions(client);
  }

  @Test
  void shouldResolveProjectIdFromEnvironmentWhenConfigOmitsIt() {
    try (var serviceOptions = mockStatic(ServiceOptions.class);
        var clientFactory = mockStatic(SecretManagerServiceClient.class)) {
      // given — the config omits the project id, so it must fall back to the environment
      serviceOptions.when(ServiceOptions::getDefaultProjectId).thenReturn("env-project");
      clientFactory
          .when(() -> SecretManagerServiceClient.create(any(SecretManagerServiceSettings.class)))
          .thenReturn(client);
      final var config = new GcpSecretManagerStoreConfig(null, "camunda-", null, null);

      // when
      final var store = GcpSecretManagerSecretStore.fromConfig(config);

      // then — the startup probe (one-by-one mode) targets the env-resolved project
      assertThat(store).isNotNull();
      final var captor = ArgumentCaptor.forClass(ListSecretsRequest.class);
      verify(client).listSecrets(captor.capture());
      assertThat(captor.getValue().getParent()).isEqualTo(ProjectName.of("env-project").toString());
    }
  }

  @Test
  void shouldFailFastWhenProjectIdIsNeitherConfiguredNorResolvableFromEnvironment() {
    try (var serviceOptions = mockStatic(ServiceOptions.class);
        var clientFactory = mockStatic(SecretManagerServiceClient.class)) {
      // given — the config omits the project id and the environment provides none either
      serviceOptions.when(ServiceOptions::getDefaultProjectId).thenReturn(null);
      final var config = new GcpSecretManagerStoreConfig(null, "camunda-", null, null);

      // when / then — a missing project id is a clear configuration error, not a deferred NPE, and
      // no client is opened
      assertThatThrownBy(() -> GcpSecretManagerSecretStore.fromConfig(config))
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageContaining("projectId");
      clientFactory.verifyNoInteractions();
    }
  }

  @Test
  void shouldFailFastWhenClientCannotBeCreated() {
    try (var clientFactory = mockStatic(SecretManagerServiceClient.class)) {
      // given — building the client fails because the Application Default Credentials chain cannot
      // produce credentials
      clientFactory
          .when(() -> SecretManagerServiceClient.create(any(SecretManagerServiceSettings.class)))
          .thenThrow(
              new IOException("no credentials in the Application Default Credentials chain"));
      final var config = new GcpSecretManagerStoreConfig(PROJECT, "camunda-", null, null);

      // when / then — a client that cannot even be constructed can never resolve a secret, so this
      // fails fast at startup rather than deferring to first use
      assertThatThrownBy(() -> GcpSecretManagerSecretStore.fromConfig(config))
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageContaining("Failed to initialize GCP Secret Manager client")
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Test
  void shouldApplyEndpointOverrideWhenAuthenticationEnabled() {
    try (var clientFactory = mockStatic(SecretManagerServiceClient.class)) {
      // given — an authenticated (production) config with an explicit endpoint override, e.g. a
      // regional or Private Service Connect endpoint
      final var settingsCaptor = ArgumentCaptor.forClass(SecretManagerServiceSettings.class);
      clientFactory
          .when(() -> SecretManagerServiceClient.create(settingsCaptor.capture()))
          .thenReturn(client);
      final var endpoint = "secretmanager.europe-west1.rep.googleapis.com:443";
      final var config = new GcpSecretManagerStoreConfig(PROJECT, "camunda-", endpoint, null);

      // when
      final var store = GcpSecretManagerSecretStore.fromConfig(config);

      // then — the override is applied to the client settings without disabling authentication
      assertThat(store).isNotNull();
      assertThat(settingsCaptor.getValue().getEndpoint()).isEqualTo(endpoint);
    }
  }

  // ---- startup connectivity probe: warns and continues (keeps the client) when the probe fails --

  @Test
  void shouldReturnAfterSuccessfulProbeWithoutClosingClient() {
    // given
    final var resolver = mock(GcpSecretResolver.class);

    // when
    GcpSecretManagerSecretStore.validateConnectivity(resolver);

    // then — the probe ran and the client stays open for the store to use
    verify(resolver).validateConnectivity();
  }

  @Test
  void shouldWarnAndContinueWithoutClosingClientWhenProbeFails() {
    // given
    final var resolver = mock(GcpSecretResolver.class);
    doThrow(apiException(Code.UNAVAILABLE)).when(resolver).validateConnectivity();

    // when — a failing startup probe must not fail fast
    GcpSecretManagerSecretStore.validateConnectivity(resolver);

    // then — the client is kept open so the error surfaces on first real use instead
    verify(resolver).validateConnectivity();
  }

  @Test
  void shouldProbeConnectivityViaListSecretsInOneByOneMode() {
    // given — one-by-one mode lists via listSecrets, so the probe must exercise that exact API
    final var resolver = new OneByOneSecretResolver(client, PROJECT, "camunda-");

    // when
    resolver.validateConnectivity();

    // then — a minimal single-result list on the configured project
    final var captor = ArgumentCaptor.forClass(ListSecretsRequest.class);
    verify(client).listSecrets(captor.capture());
    assertThat(captor.getValue().getParent()).isEqualTo(ProjectName.of(PROJECT).toString());
    assertThat(captor.getValue().getPageSize()).isEqualTo(1);
  }

  @Test
  void shouldProbeConnectivityViaAccessSecretVersionInContainerMode() {
    // given — container mode reads exactly one secret, so the probe must access that container
    final var resolver = new ContainerSecretResolver(client, PROJECT, "", "app-config");
    when(client.accessSecretVersion(any(SecretVersionName.class))).thenReturn(response("{}"));

    // when
    resolver.validateConnectivity();

    // then — accessSecretVersion targets the container secret
    final var captor = ArgumentCaptor.forClass(SecretVersionName.class);
    verify(client).accessSecretVersion(captor.capture());
    assertThat(captor.getValue().getSecret()).isEqualTo("app-config");
  }

  private void mockListSecrets(final String... secretIds) {
    final var paged = mock(ListSecretsPagedResponse.class);
    final var secrets =
        Arrays.stream(secretIds)
            .map(
                id -> Secret.newBuilder().setName("projects/" + PROJECT + "/secrets/" + id).build())
            .toList();
    when(paged.iterateAll()).thenReturn(secrets);
    when(client.listSecrets(any(ProjectName.class))).thenReturn(paged);
  }

  private static AccessSecretVersionResponse response(final String value) {
    return AccessSecretVersionResponse.newBuilder()
        .setPayload(SecretPayload.newBuilder().setData(ByteString.copyFromUtf8(value)).build())
        .build();
  }

  private static ApiException apiException(final Code code) {
    return new ApiException(new RuntimeException("boom"), statusCode(code), false);
  }

  private static StatusCode statusCode(final Code code) {
    return new StatusCode() {
      @Override
      public Code getCode() {
        return code;
      }

      @Override
      public Object getTransportCode() {
        return null;
      }
    };
  }
}
