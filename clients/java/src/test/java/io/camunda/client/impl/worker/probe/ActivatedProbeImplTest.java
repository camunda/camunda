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
package io.camunda.client.impl.worker.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.protocol.rest.ActivatedProbeResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ActivatedProbeImplTest {

  private static final CamundaObjectMapper JSON_MAPPER = new CamundaObjectMapper();

  @Test
  void shouldRemoveTheValuesAtEveryPointer() {
    // given
    final ActivatedProbeImpl probe =
        probe(
            "{\"a/b\":\"slash-secret\",\"m~n\":\"tilde-secret\","
                + "\"list\":[\"plain\",\"list-secret\"],\"nested\":{\"key\":\"nested-secret\"}}",
            "/a~1b",
            "/m~0n",
            "/list/1",
            "/nested/key");

    // when
    final String printed = probe.toString();
    final String message =
        probe.withoutSecrets("slash-secret, tilde-secret, list-secret, nested-secret, plain");

    // then
    assertThat(printed)
        .doesNotContain("slash-secret", "tilde-secret", "list-secret", "nested-secret")
        .contains("plain");
    assertThat(message).isEqualTo("***, ***, ***, ***, plain");
    assertThat(probe.toJson())
        .contains("slash-secret", "tilde-secret", "list-secret", "nested-secret");
  }

  @Test
  void shouldRemoveTheLongestValueFirst() {
    // given
    final ActivatedProbeImpl probe =
        probe("{\"short\":\"key\",\"long\":\"key-and-more\"}", "/short", "/long");

    // when
    final String message = probe.withoutSecrets("got key-and-more");

    // then
    assertThat(message).isEqualTo("got ***");
  }

  @Test
  void shouldKeepTheInputWhenAPointerMatchesNothing() {
    // given
    final ActivatedProbeImpl probe = probe("{\"region\":\"eu-west-1\"}", "/missing", "/region/0");

    // when
    final String message = probe.withoutSecrets("in eu-west-1");

    // then
    assertThat(message).isEqualTo("in eu-west-1");
    assertThat(probe.toString()).contains("eu-west-1");
  }

  @Test
  void shouldNotChangeTheInputWhenPrinting() {
    // given
    final ActivatedProbeImpl probe = probe("{\"secretKey\":\"value\"}", "/secretKey");

    // when
    probe.toString();

    // then
    assertThat(probe.getInputAsMap()).isEqualTo(Collections.singletonMap("secretKey", "value"));
  }

  @Test
  void shouldRemoveTheEscapedValuesFromAFailedConversion() {
    // given - a failed conversion prints the input as JSON, with the secret escaped
    final String secret = "pa\"ss\\wo\nrd";
    final ActivatedProbeImpl probe =
        probe(JSON_MAPPER.toJson(Collections.singletonMap("port", secret)), "/port");
    final Throwable failure = catchThrowable(() -> probe.getInputAsType(Port.class));
    assertThat(failure.getMessage()).contains("pa\\\"ss\\\\wo\\nrd");

    // when
    final String stackTrace = ProbeExceptionHandlerImpl.stackTraceWithoutSecrets(probe, failure);

    // then
    assertThat(stackTrace)
        .contains("Failed to deserialize")
        .doesNotContain("pa\"ss", "pa\\\"ss", "wo\nrd", "wo\\nrd");
  }

  private static ActivatedProbeImpl probe(final String input, final String... pointers) {
    final List<String> secretPointers = Arrays.asList(pointers);
    final ActivatedProbeResult result =
        new ActivatedProbeResult()
            .probeKey("1")
            .probeId("probe")
            .worker("worker")
            .deadline(1L)
            .tenantId("<default>")
            .physicalTenantId("default")
            .input(JSON_MAPPER.fromJsonAsMap(input))
            .secretPointers(secretPointers);
    return new ActivatedProbeImpl(JSON_MAPPER, result);
  }

  public static final class Port {
    public int port;
  }
}
