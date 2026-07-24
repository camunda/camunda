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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient.ListSecretsPagedResponse;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
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

    // when / then
    assertThatThrownBy(store::list).isInstanceOf(SecretStoreUnavailableException.class);
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
