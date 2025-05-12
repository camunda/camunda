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
package io.camunda.client.group;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class UnassignMemberGroupTest extends ClientRestTest {

  public static final String GROUP_ID = "groupId";
  public static final String USERNAME = "username";
  public static final String MAPPING_ID = "mappingRuleId";

  @Test
  void shouldUnassignUserFromGroup() {
    // when
    client.newUnassignUserFromGroupCommand(GROUP_ID).username(USERNAME).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl().contains(GROUP_ID + "/users/" + USERNAME)).isTrue();
  }

  @Test
  void shouldRaiseExceptionOnRequestErrorUnassignUser() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/users/" + USERNAME;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () -> client.newUnassignUserFromGroupCommand(GROUP_ID).username(USERNAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnForbiddenRequestUnassignUser() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/users/" + USERNAME;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Forbidden").status(403));

    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToGroupCommand(GROUP_ID).username(USERNAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 403: 'Forbidden'");
  }

  @Test
  void shouldHandleValidationErrorResponseUnassignUser() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/users/" + USERNAME;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Bad Request").status(400));

    // when / then
    assertThatThrownBy(
            () -> client.newAssignUserToGroupCommand(GROUP_ID).username(USERNAME).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }

  @Test
  void shouldUnassignMappingFromGroup() {
    // when
    client.newUnassignMappingFromGroupCommand(GROUP_ID).mappingRuleId(MAPPING_ID).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl().contains(GROUP_ID + "/mapping-rules/" + MAPPING_ID)).isTrue();
  }

  @Test
  void shouldRaiseExceptionOnRequestErrorUnassignMapping() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/mapping-rules/" + MAPPING_ID;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingFromGroupCommand(GROUP_ID)
                    .mappingRuleId(MAPPING_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnForbiddenRequestUnassignMapping() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/mapping-rules/" + MAPPING_ID;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Forbidden").status(403));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingFromGroupCommand(GROUP_ID)
                    .mappingRuleId(MAPPING_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 403: 'Forbidden'");
  }

  @Test
  void shouldHandleValidationErrorResponseUnassignMapping() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/mapping-rules/" + MAPPING_ID;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Bad Request").status(400));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingFromGroupCommand(GROUP_ID)
                    .mappingRuleId(MAPPING_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }
}
