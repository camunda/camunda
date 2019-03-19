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

import static io.zeebe.test.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PublishMessageTest extends ClientTest {

  @Test
  public void shouldPublishMessage() {
    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .timeToLive(Duration.ofDays(1))
        .messageId("theId")
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(request.getName()).isEqualTo("name");
    assertThat(request.getCorrelationKey()).isEqualTo("key");
    assertThat(request.getMessageId()).isEqualTo("theId");
    assertThat(request.getTimeToLive()).isEqualTo(Duration.ofDays(1).toMillis());
  }

  @Test
  public void shouldPublishMessageWithStringVariables() {
    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variables("{\"foo\":\"bar\"}")
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithInputStreamVariables() {
    // given
    final String variables = "{\"foo\":\"bar\"}";
    final ByteArrayInputStream byteArrayInputStream =
        new ByteArrayInputStream(variables.getBytes());

    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variables(byteArrayInputStream)
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithMapVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("foo", "bar");

    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variables(variables)
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldPublishMessageWithObjectVariables() {
    // when
    client
        .newPublishMessageCommand()
        .messageName("name")
        .correlationKey("key")
        .variables(new Variables())
        .send()
        .join();

    // then
    final PublishMessageRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getVariables())).contains(entry("foo", "bar"));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        PublishMessageRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () ->
                client
                    .newPublishMessageCommand()
                    .messageName("name")
                    .correlationKey("key")
                    .messageId("foo")
                    .send()
                    .join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  public static class Variables {

    private final String foo = "bar";

    Variables() {}

    public String getFoo() {
      return foo;
    }
  }
}
