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
package io.camunda.client.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.protocol.rest.*;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.*;
import org.junit.jupiter.api.Test;

public class QueryBatchOperationTest extends ClientRestTest {

  @Test
  public void shouldGetBatchOperationByKey() {
    // when
    client.newBatchOperationGetRequest(123L).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl()).isEqualTo("/v2/batch-operations/123");
    assertThat(request.getBodyAsString()).isEmpty();
  }

  @Test
  public void shouldGetBatchOperationItemsByKey() {
    // when
    client.newBatchOperationItemsGetRequest(123L).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl()).isEqualTo("/v2/batch-operations/123/items");
    assertThat(request.getBodyAsString()).isEmpty();
  }
}
