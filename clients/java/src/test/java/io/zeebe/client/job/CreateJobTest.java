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
package io.zeebe.client.job;

import static io.zeebe.test.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.google.common.base.Charsets;
import io.zeebe.client.api.response.CreateJobResponse;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.util.ClientTest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateJobRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class CreateJobTest extends ClientTest {

  @Test
  public void shouldCreateJob() {
    // given
    gatewayService.onCreateJobRequest(1, 123);

    final Map<String, Object> partialCustomHeaders = new HashMap<>();
    partialCustomHeaders.put("one", 1);
    partialCustomHeaders.put("two", "II");

    // when
    final CreateJobResponse response =
        client
            .jobClient()
            .newCreateCommand()
            .jobType("testJob")
            .retries(12)
            .addCustomHeader("foo", "bar")
            .addCustomHeaders(partialCustomHeaders)
            .addCustomHeader("hello", "world")
            .send()
            .join();

    // then
    assertThat(response.getPartitionId()).isEqualTo(1);
    assertThat(response.getKey()).isEqualTo(123);

    final CreateJobRequest request = gatewayService.getLastRequest();
    assertThat(request.getJobType()).isEqualTo("testJob");
    assertThat(request.getRetries()).isEqualTo(12);

    assertThat(fromJsonAsMap(request.getCustomHeaders()))
        .containsOnly(
            entry("one", 1), entry("two", "II"), entry("foo", "bar"), entry("hello", "world"));
  }

  @Test
  public void shouldCreateJobWithStringPayload() {
    // when
    client
        .jobClient()
        .newCreateCommand()
        .jobType("testJob")
        .payload("{\"foo\": \"bar\"}")
        .send()
        .join();

    // then
    final CreateJobRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getPayload())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateJobWithInputStreamPayload() {
    // given
    final String payload = "{\"foo\": \"bar\"}";
    final InputStream inputStream = new ByteArrayInputStream(payload.getBytes(Charsets.UTF_8));

    // when
    client.jobClient().newCreateCommand().jobType("testJob").payload(inputStream).send().join();

    // then
    final CreateJobRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getPayload())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateJobWithMapPayload() {
    // when
    client
        .jobClient()
        .newCreateCommand()
        .jobType("testJob")
        .payload(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    final CreateJobRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getPayload())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldCreateJobWithObjectPayload() {
    // when
    client.jobClient().newCreateCommand().jobType("testJob").payload(new Payload()).send().join();

    // then
    final CreateJobRequest request = gatewayService.getLastRequest();
    assertThat(fromJsonAsMap(request.getPayload())).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        CreateJobRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () ->
                client
                    .jobClient()
                    .newCreateCommand()
                    .jobType("testJob")
                    .payload("[]")
                    .send()
                    .join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  public static class Payload {

    private final String foo = "bar";

    Payload() {}

    public String getFoo() {
      return foo;
    }
  }
}
