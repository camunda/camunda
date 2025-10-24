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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.SignalBroadcastRequest;
import io.camunda.client.protocol.rest.SignalBroadcastResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class BroadcastSignalRestTest extends ClientRestTest {

  private static final SignalBroadcastResult DUMMY_RESPONSE =
      Instancio.create(SignalBroadcastResult.class).signalKey("1");

  @Test
  public void shouldBroadcastSignalWithStringVariables() {
    // given
    gatewayService.onBroadcastSignalRequest(DUMMY_RESPONSE);

    // when
    client
        .newBroadcastSignalCommand()
        .signalName("name")
        .variables("{\"foo\":\"bar\"}")
        .send()
        .join();

    // then
    final SignalBroadcastRequest request =
        gatewayService.getLastRequest(SignalBroadcastRequest.class);
    Assertions.assertThat(request.getVariables()).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldBroadcastSignalWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\":\"bar\"}";
    final ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(variables.getBytes());
    gatewayService.onBroadcastSignalRequest(DUMMY_RESPONSE);

    // when
    client
        .newBroadcastSignalCommand()
        .signalName("name")
        .variables(byteArrayInputStream)
        .send()
        .join();

    // then
    final SignalBroadcastRequest request =
        gatewayService.getLastRequest(SignalBroadcastRequest.class);
    Assertions.assertThat(request.getVariables()).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldBroadcastSignalWithMapVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");
    gatewayService.onBroadcastSignalRequest(DUMMY_RESPONSE);

    // when
    client.newBroadcastSignalCommand().signalName("name").variables(variables).send().join();

    // then
    final SignalBroadcastRequest request =
        gatewayService.getLastRequest(SignalBroadcastRequest.class);
    Assertions.assertThat(request.getVariables()).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldBroadcastSignalWithObjectVariables() {
    // given
    gatewayService.onBroadcastSignalRequest(DUMMY_RESPONSE);

    // when
    client
        .newBroadcastSignalCommand()
        .signalName("name")
        .variables(new BroadcastSignalRestTest.Variables())
        .send()
        .join();

    // then
    final SignalBroadcastRequest request =
        gatewayService.getLastRequest(SignalBroadcastRequest.class);
    Assertions.assertThat(request.getVariables()).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldBroadcastSignalWithSingleVariable() {
    // given
    gatewayService.onBroadcastSignalRequest(DUMMY_RESPONSE);

    // when
    final String key = "key";
    final String value = "value";
    client.newBroadcastSignalCommand().signalName("name").variable(key, value).send().join();

    // then
    final SignalBroadcastRequest request =
        gatewayService.getLastRequest(SignalBroadcastRequest.class);
    Assertions.assertThat(request.getVariables()).containsOnly(entry(key, value));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getBroadcastSignalUrl(),
        () -> new ProblemDetail().title("Invalid request").status(400));

    // when
    assertThatThrownBy(() -> client.newBroadcastSignalCommand().signalName("name").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldAllowSpecifyingTenantIdBy() {
    // given
    gatewayService.onBroadcastSignalRequest(DUMMY_RESPONSE);

    // when
    client.newBroadcastSignalCommand().signalName("").tenantId("custom-tenant").send().join();

    // then
    final SignalBroadcastRequest request =
        gatewayService.getLastRequest(SignalBroadcastRequest.class);
    assertThat(request.getTenantId()).isEqualTo("custom-tenant");
  }

  public static class Variables {

    Variables() {}

    public String getFoo() {
      return "bar";
    }
  }
}
