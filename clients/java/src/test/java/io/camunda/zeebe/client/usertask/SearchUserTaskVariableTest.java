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
package io.camunda.zeebe.client.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.protocol.rest.VariableSearchQueryRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public final class SearchUserTaskVariableTest extends ClientRestTest {

  @Test
  void shouldSearchVariables() {
    final long userTaskKey = 1L;

    // when
    client.newUserTaskVariableQuery(userTaskKey).send().join();

    // then
    final VariableSearchQueryRequest request =
        gatewayService.getLastRequest(VariableSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }
}
