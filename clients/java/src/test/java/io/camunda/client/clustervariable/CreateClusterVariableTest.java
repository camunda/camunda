/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateClusterVariableResponse;
import io.camunda.client.api.search.enums.ClusterVariableKind;
import io.camunda.client.protocol.rest.ClusterVariableKindEnum;
import io.camunda.client.protocol.rest.ClusterVariableResult;
import io.camunda.client.protocol.rest.ClusterVariableScopeEnum;
import io.camunda.client.protocol.rest.CreateClusterVariableRequest;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.util.HashMap;
import java.util.Map;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class CreateClusterVariableTest extends ClientRestTest {

  private static final String VARIABLE_NAME = "myVariable";
  private static final Object VARIABLE_VALUE = "myValue";
  private static final String TENANT_ID = "tenant_1";

  @Test
  void shouldCreateGlobalScopedClusterVariable() {
    // given
    gatewayService.onCreateGlobalClusterVariableRequest(
        Instancio.create(ClusterVariableResult.class).scope(ClusterVariableScopeEnum.GLOBAL));

    // when
    client
        .newGloballyScopedClusterVariableCreateRequest()
        .create(VARIABLE_NAME, VARIABLE_VALUE)
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesCreateGlobalUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldCreateTenantScopedClusterVariable() {
    // given
    gatewayService.onCreateTenantClusterVariableRequest(
        TENANT_ID,
        Instancio.create(ClusterVariableResult.class).scope(ClusterVariableScopeEnum.TENANT));

    // when
    client
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID)
        .create(VARIABLE_NAME, VARIABLE_VALUE)
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(RestGatewayPaths.getClusterVariablesCreateTenantUrl(TENANT_ID));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldRaiseExceptionOnRequestError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getClusterVariablesCreateGlobalUrl(),
        () -> new ProblemDetail().title("Bad Request").status(400));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create(VARIABLE_NAME, VARIABLE_VALUE)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }

  @Test
  void shouldRaiseExceptionOnServerError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getClusterVariablesCreateGlobalUrl(),
        () -> new ProblemDetail().title("Internal Server Error").status(500));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create(VARIABLE_NAME, VARIABLE_VALUE)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 500: 'Internal Server Error'");
  }

  @Test
  void shouldCreateGlobalClusterVariableWithKind() {
    // given
    final ClusterVariableResult responseProto =
        Instancio.create(ClusterVariableResult.class)
            .scope(ClusterVariableScopeEnum.GLOBAL)
            .kind(ClusterVariableKindEnum.SECRET_REFERENCE);
    gatewayService.onCreateGlobalClusterVariableRequest(responseProto);

    // when
    final CreateClusterVariableResponse response =
        client
            .newGloballyScopedClusterVariableCreateRequest()
            .create(VARIABLE_NAME, VARIABLE_VALUE)
            .kind(ClusterVariableKind.SECRET_REFERENCE)
            .send()
            .join();

    // then
    assertThat(response.getKind()).isEqualTo(ClusterVariableKind.SECRET_REFERENCE);
    final CreateClusterVariableRequest sentRequest =
        gatewayService.getLastRequest(CreateClusterVariableRequest.class);
    assertThat(sentRequest.getKind()).isEqualTo(ClusterVariableKindEnum.SECRET_REFERENCE);
  }

  @Test
  void shouldCreateTenantClusterVariableWithKind() {
    // given
    final ClusterVariableResult responseProto =
        Instancio.create(ClusterVariableResult.class)
            .scope(ClusterVariableScopeEnum.TENANT)
            .kind(ClusterVariableKindEnum.SECRET_REFERENCE);
    gatewayService.onCreateTenantClusterVariableRequest(TENANT_ID, responseProto);

    // when
    final CreateClusterVariableResponse response =
        client
            .newTenantScopedClusterVariableCreateRequest(TENANT_ID)
            .create(VARIABLE_NAME, VARIABLE_VALUE)
            .kind(ClusterVariableKind.SECRET_REFERENCE)
            .send()
            .join();

    // then
    assertThat(response.getKind()).isEqualTo(ClusterVariableKind.SECRET_REFERENCE);
    final CreateClusterVariableRequest sentRequest =
        gatewayService.getLastRequest(CreateClusterVariableRequest.class);
    assertThat(sentRequest.getKind()).isEqualTo(ClusterVariableKindEnum.SECRET_REFERENCE);
  }

  @Test
  void shouldCreateGlobalClusterVariableWithMetadata() {
    // given
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("kind", "CREDENTIAL");
    metadata.put("schemaVersion", 2);
    gatewayService.onCreateGlobalClusterVariableRequest(
        Instancio.create(ClusterVariableResult.class).scope(ClusterVariableScopeEnum.GLOBAL));

    // when
    client
        .newGloballyScopedClusterVariableCreateRequest()
        .create(VARIABLE_NAME, VARIABLE_VALUE)
        .metadata(metadata)
        .send()
        .join();

    // then
    final CreateClusterVariableRequest sentRequest =
        gatewayService.getLastRequest(CreateClusterVariableRequest.class);
    assertThat(sentRequest.getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
  }

  @Test
  void shouldCreateTenantClusterVariableWithMetadata() {
    // given
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("kind", "CREDENTIAL");
    metadata.put("schemaVersion", 2);
    gatewayService.onCreateTenantClusterVariableRequest(
        TENANT_ID,
        Instancio.create(ClusterVariableResult.class).scope(ClusterVariableScopeEnum.TENANT));

    // when
    client
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID)
        .create(VARIABLE_NAME, VARIABLE_VALUE)
        .metadata(metadata)
        .send()
        .join();

    // then
    final CreateClusterVariableRequest sentRequest =
        gatewayService.getLastRequest(CreateClusterVariableRequest.class);
    assertThat(sentRequest.getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
  }

  @Test
  void shouldRaiseExceptionOnNullVariableName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create(null, VARIABLE_VALUE)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyVariableName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create("", VARIABLE_VALUE)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }
}
