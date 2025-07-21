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

public class AssignMemberGroupTest extends ClientRestTest {

  public static final String GROUP_ID = "groupId";
  public static final String USERNAME = "username";
  public static final String CLIENT_ID = "clientId";
  public static final String MAPPING_ID = "mappingId";

  @Test
  void shouldAssignUserToGroup() {
    // when
    client.newAssignUserToGroupCommand().username(USERNAME).groupId(GROUP_ID).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl().contains(GROUP_ID + "/users/" + USERNAME)).isTrue();
  }

  @Test
  void shouldRaiseExceptionOnRequestErrorAssignUser() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/users/" + USERNAME;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignUserToGroupCommand()
                    .username(USERNAME)
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionIfGroupNotExistsAssignUser() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/users/" + USERNAME;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Conflict").status(409));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignUserToGroupCommand()
                    .username(USERNAME)
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  @Test
  void shouldHandleValidationErrorResponseAssignUser() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/users/" + USERNAME;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Bad Request").status(400));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignUserToGroupCommand()
                    .username(USERNAME)
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }

  @Test
  void shouldAssignMappingToGroup() {
    // when
    client.newAssignMappingToGroupCommand().mappingId(MAPPING_ID).groupId(GROUP_ID).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl().contains(GROUP_ID + "/mapping-rules/" + MAPPING_ID)).isTrue();
  }

  @Test
  void shouldRaiseExceptionOnRequestErrorAssigningMapping() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/mapping-rules/" + MAPPING_ID;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToGroupCommand()
                    .mappingId(MAPPING_ID)
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionIfGroupNotExistsAssigningMapping() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/mapping-rules/" + MAPPING_ID;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Conflict").status(409));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToGroupCommand()
                    .mappingId(MAPPING_ID)
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  @Test
  void shouldHandleValidationErrorResponseAssigningMapping() {
    // given
    final String path = REST_API_PATH + "/groups/" + GROUP_ID + "/mapping-rules/" + MAPPING_ID;
    gatewayService.errorOnRequest(path, () -> new ProblemDetail().title("Bad Request").status(400));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToGroupCommand()
                    .mappingId(MAPPING_ID)
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 400: 'Bad Request'");
  }

  @Test
  void shouldRaiseExceptionOnNullMappingIdWhenAssigningMappingToGroup() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToGroupCommand()
                    .mappingId(null)
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyMappingIdWhenAssigningMappingToGroup() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToGroupCommand()
                    .mappingId("")
                    .groupId(GROUP_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullGroupIdWhenAssigningMappingToGroup() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToGroupCommand()
                    .mappingId(MAPPING_ID)
                    .groupId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyGroupIdWhenAssigningMappingToGroup() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignMappingToGroupCommand()
                    .mappingId(MAPPING_ID)
                    .groupId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  @Test
  void shouldAssignClientToGroup() {
    // when
    client.newAssignClientToGroupCommand().clientId(CLIENT_ID).groupId(GROUP_ID).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl().contains(GROUP_ID + "/clients/" + CLIENT_ID)).isTrue();
  }

  @Test
  void shouldRaiseExceptionOnNullClientIdWhenAssigningClientToGroup() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignClientToGroupCommand()
                    .clientId(null)
                    .groupId("groupId")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyClientIdWhenAssigningClientToGroup() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignClientToGroupCommand()
                    .clientId("")
                    .groupId("groupId")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullGroupIdWhenAssigningClientToGroup() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignClientToGroupCommand()
                    .clientId(CLIENT_ID)
                    .groupId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyGroupIdWhenAssigningClientToGroup() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignClientToGroupCommand()
                    .clientId(CLIENT_ID)
                    .groupId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }
}
