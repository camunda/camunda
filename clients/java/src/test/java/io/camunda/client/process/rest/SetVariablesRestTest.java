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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.protocol.rest.SetVariableRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.StringUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class SetVariablesRestTest extends ClientRestTest {

  @Test
  public void shouldCommandWithVariablesAsString() {
    // given
    final String variables = "{\"key\": \"val\"}";

    // when
    client.newSetVariablesCommand(123).variables(variables).send().join();

    // thenv
    final SetVariableRequest request = gatewayService.getLastRequest(SetVariableRequest.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry("key", "val"));
  }

  @Test
  public void shouldCommandWithVariablesAsStream() {
    // given
    final String variables = "{\"key\": \"val\"}";
    final InputStream variablesStream = new ByteArrayInputStream(StringUtil.getBytes(variables));

    // when
    client.newSetVariablesCommand(123).variables(variablesStream).send().join();

    // then
    final SetVariableRequest request = gatewayService.getLastRequest(SetVariableRequest.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry("key", "val"));
  }

  @Test
  public void shouldCommandWithVariablesAsMap() {
    // given
    final Map<String, Object> variablesMap = Collections.singletonMap("key", "val");

    // when
    client.newSetVariablesCommand(123).variables(variablesMap).send().join();

    // then
    final SetVariableRequest request = gatewayService.getLastRequest(SetVariableRequest.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry("key", "val"));
  }

  @Test
  public void shouldCommandWithVariablesAsObject() {
    // given
    final SetVariablesRestTest.Document document = new SetVariablesRestTest.Document("val");

    // when
    client.newSetVariablesCommand(123).variables(document).send().join();

    // then
    final SetVariableRequest request = gatewayService.getLastRequest(SetVariableRequest.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry("key", "val"));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // when
    assertThatThrownBy(() -> client.newSetVariablesCommand(123).variables("[]").send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Failed to deserialize json '[]' to 'Map<String, Object>'");
  }

  static class Document {
    public final String key;

    Document(final String key) {
      this.key = key;
    }
  }
}
