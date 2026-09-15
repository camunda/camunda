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
package io.camunda.runner.upstreambugs;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Reproducer for <b>Bug 2</b> in {@code clients/java-runner/UPSTREAM_BUGS.md} — the public REST
 * API's {@code ProcessInstanceStateEnum} doesn't accept {@code "CANCELED"} even though the internal
 * exporter and Operate's UI both use that spelling.
 *
 * <p>The Operate UI sends {@code "CANCELED"} as a state filter when its "Canceled" tab is active.
 * The gateway returns 400 with {@code Unexpected value 'CANCELED'} from {@code
 * io.camunda.gateway.protocol.model.ProcessInstanceStateEnum.fromValue}. Internal background
 * queries fail; the broker logs the parse error.
 *
 * <p>This test bypasses the SDK (which doesn't have {@code CANCELED} in its enum either) and sends
 * the raw request that Operate sends. With the bug present the response is 400; with the fix it
 * should be 2xx.
 *
 * <p><b>Status</b>: {@link Disabled} until the upstream fix lands. To verify a fix, enable and run
 * against any Camunda 8.x broker on {@code localhost:8080}.
 */
@Tag("upstream-bug-reproducer")
@Disabled("Reproduces upstream Bug 2 — gateway rejects CANCELED state. Enable to verify the fix.")
final class ProcessInstanceCanceledStateEnumIT {

  private static final String BODY =
      """
      {"filter": {"state": "CANCELED"}}
      """;

  @Test
  void shouldAcceptCanceledStateOnProcessInstanceSearch() throws Exception {
    final HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:8080/v2/process-instances/search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(BODY))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as(
            "Gateway should accept CANCELED on /v2/process-instances/search "
                + "(it's the spelling the internal exporter and Operate UI both use). "
                + "Got %d with body: %s",
            response.statusCode(), response.body())
        .isBetween(200, 299);
  }
}
