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
package io.camunda.zeebe.client.process.rest;

import static io.camunda.zeebe.client.util.assertions.LoggedRequestAssert.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayPaths;
import io.camunda.zeebe.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class ResolveIncidentRestTest extends ClientRestTest {

  @Test
  public void shouldSendCommand() {
    // given
    final int incidentKey = 123;

    // when
    client.newResolveIncidentCommand(incidentKey).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getIncidentResolutionUrl(incidentKey))
        .hasEmptyBody();
  }
}
