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
package io.zeebe.client.workflow;

import static io.zeebe.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.response.SetVariablesResponse;
import io.zeebe.client.util.ClientTest;
import io.zeebe.client.util.StringUtil;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public final class SetVariablesTest extends ClientTest {

  @Test
  public void shouldCommandWithVariablesAsString() {
    // given
    gatewayService.onSetVariablesRequest(345);
    final String variables = "{\"key\": \"val\"}";

    // when
    final SetVariablesResponse response =
        client.newSetVariablesCommand(123).variables(variables).send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getKey()).isEqualTo(345);

    final SetVariablesRequest request = gatewayService.getLastRequest();
    assertThat(request.getElementInstanceKey()).isEqualTo(123);
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("key", "val"));

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldCommandWithVariablesAsStream() {
    // given
    final String variables = "{\"key\": \"val\"}";
    final InputStream variablesStream = new ByteArrayInputStream(StringUtil.getBytes(variables));

    // when
    client.newSetVariablesCommand(123).variables(variablesStream).send().join();

    // then
    final SetVariablesRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("key", "val"));
  }

  @Test
  public void shouldCommandWithVariablesAsMap() {
    // given
    final Map<String, Object> variablesMap = Collections.singletonMap("key", "val");

    // when
    client.newSetVariablesCommand(123).variables(variablesMap).send().join();

    // then
    final SetVariablesRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("key", "val"));
  }

  @Test
  public void shouldCommandWithVariablesAsObject() {
    // given
    final Document document = new Document("val");

    // when
    client.newSetVariablesCommand(123).variables(document).send().join();

    // then
    final SetVariablesRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).containsOnly(entry("key", "val"));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        SetVariablesRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(() -> client.newSetVariablesCommand(123).variables("[]").send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newSetVariablesCommand(123).variables("{}").requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  static class Document {
    public final String key;

    Document(final String key) {
      this.key = key;
    }
  }
}
