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
package io.camunda.client.resource.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.protocol.rest.DeleteResourceRequest;
import io.camunda.client.protocol.rest.DeleteResourceResponse;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class DeleteResourceRestTest extends ClientRestTest {

  private static final DeleteResourceResponse DUMMY_RESPONSE =
      new DeleteResourceResponse().resourceKey("1");

  @Test
  public void shouldSendCommand() {
    final long resourceKey = 123L;
    gatewayService.onDeleteResourceRequest(resourceKey, DUMMY_RESPONSE);

    // when
    client.newDeleteResourceCommand(resourceKey).operationReference(1L).send().join();

    // then
    final DeleteResourceRequest request =
        gatewayService.getLastRequest(DeleteResourceRequest.class);
    assertThat(request.getOperationReference()).isEqualTo(1L);
  }
}
