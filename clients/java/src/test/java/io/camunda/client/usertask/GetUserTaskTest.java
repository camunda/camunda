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
package io.camunda.client.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.protocol.rest.UserTaskResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.time.OffsetDateTime;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class GetUserTaskTest extends ClientRestTest {
  @Test
  void shouldGetUserTask() {
    // given
    final long userTaskKey = 1L;
    gatewayService.onUserTaskRequest(
        userTaskKey,
        Instancio.create(UserTaskResult.class)
            .formKey("1")
            .elementInstanceKey("2")
            .processDefinitionKey("3")
            .processInstanceKey("4")
            .userTaskKey("5")
            .creationDate(OffsetDateTime.now().toString())
            .completionDate(OffsetDateTime.now().toString())
            .dueDate(OffsetDateTime.now().toString())
            .followUpDate(OffsetDateTime.now().toString())
            .rootProcessInstanceKey("6"));

    // when
    client.newUserTaskGetRequest(userTaskKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getUserTaskUrl(userTaskKey));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }
}
