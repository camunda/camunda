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
package io.camunda.client.process.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.protocol.rest.ProcessInstanceBusinessIdAssignmentInstruction;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class AssignProcessInstanceBusinessIdRestTest extends ClientRestTest {

  private static final Long PI_KEY = 1L;
  private static final String BUSINESS_ID = "order-4711";

  @Test
  public void shouldAssignBusinessId() {
    // when
    client.newAssignProcessInstanceBusinessIdCommand(PI_KEY).businessId(BUSINESS_ID).send().join();

    // then
    final ProcessInstanceBusinessIdAssignmentInstruction request =
        gatewayService.getLastRequest(ProcessInstanceBusinessIdAssignmentInstruction.class);
    assertThat(request.getBusinessId()).isEqualTo(BUSINESS_ID);
  }
}
