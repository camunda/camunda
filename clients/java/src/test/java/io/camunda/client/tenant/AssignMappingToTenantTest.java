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
package io.camunda.client.tenant;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class AssignMappingToTenantTest extends ClientRestTest {

  private static final String TENANT_ID = "tenantId";
  private static final String MAPPING_ID = "mappingId";

  @Test
  void shouldAssignMappingToTenant() {
    // when
    client.newAssignMappingToTenantCommand(TENANT_ID).mappingId(MAPPING_ID).send().join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath)
        .isEqualTo(REST_API_PATH + "/tenants/" + TENANT_ID + "/mappings/" + MAPPING_ID);
  }

  @Test
  void shouldRaiseExceptionOnNotFoundTenant() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_ID + "/mappings/" + MAPPING_ID,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToTenantCommand(TENANT_ID)
                    .mappingId(MAPPING_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnNotFoundMapping() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_ID + "/mappings/" + MAPPING_ID,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToTenantCommand(TENANT_ID)
                    .mappingId(MAPPING_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldHandleServerError() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_ID + "/mappings/" + MAPPING_ID,
        () -> new ProblemDetail().title("Internal Server Error").status(500));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToTenantCommand(TENANT_ID)
                    .mappingId(MAPPING_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 500: 'Internal Server Error'");
  }

  @Test
  void shouldRaiseExceptionOnForbiddenRequest() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/tenants/" + TENANT_ID + "/mappings/" + MAPPING_ID,
        () -> new ProblemDetail().title("Forbidden").status(403));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToTenantCommand(TENANT_ID)
                    .mappingId(MAPPING_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 403: 'Forbidden'");
  }
}
