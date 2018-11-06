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
package io.zeebe.broker.it.job;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCreated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.cmd.ClientException;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class CreateJobTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Rule public Timeout testTimeout = Timeout.seconds(15);

  @Test
  public void shouldCreateJob() {
    // given
    final JobClient jobClient = clientRule.getClient().jobClient();

    // when
    final JobEvent jobEvent =
        jobClient
            .newCreateCommand()
            .jobType("foo")
            .addCustomHeader("k1", "a")
            .addCustomHeader("k2", "b")
            .send()
            .join();

    // then
    assertThat(jobEvent).isNotNull();
    assertThat(jobEvent.getKey()).isGreaterThanOrEqualTo(0);

    assertJobCreated(
        "foo",
        (job) -> {
          assertThat(job.getType()).isEqualTo("foo");
          assertThat(job.getCustomHeaders()).containsOnly(entry("k1", "a"), entry("k2", "b"));
          assertThat(job.getWorker()).isEmpty();
          assertThat(job.getDeadline()).isNull();
          assertThat(job.getRetries()).isEqualTo(3);

          assertThat(job.getPayload()).isEqualTo("{}");
          assertThat(job.getPayloadAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldCompleteJobNullPayload() {
    // given
    final JobClient jobClient = clientRule.getClient().jobClient();

    // when
    jobClient.newCreateCommand().jobType("foo").payload("null").send().join();

    // then
    assertJobCreated(
        "foo",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{}");
          assertThat(job.getPayloadAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldCreateJobWithPayload() {
    // given
    final JobClient jobClient = clientRule.getClient().jobClient();

    // when
    jobClient.newCreateCommand().jobType("foo").payload("{\"foo\":\"bar\"}").send().join();

    // then
    assertJobCreated(
        "foo",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldThrowExceptionOnCompleteJobWithInvalidPayload() {
    // given
    final JobClient jobClient = clientRule.getClient().jobClient();

    // when
    final Throwable throwable =
        catchThrowable(
            () -> jobClient.newCreateCommand().jobType("foo").payload("[]").send().join());

    // then
    assertThat(throwable).isInstanceOf(ClientException.class);
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldCreateJobWithPayloadAsMap() {
    // given
    final JobClient jobClient = clientRule.getClient().jobClient();

    // when
    jobClient
        .newCreateCommand()
        .jobType("foo")
        .payload(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    assertJobCreated(
        "foo",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldCreateJobWithPayloadAsObject() {
    // given
    final JobClient jobClient = clientRule.getClient().jobClient();

    final PayloadObject payload = new PayloadObject();
    payload.foo = "bar";

    // when
    jobClient.newCreateCommand().jobType("foo").payload(payload).send().join();

    // then
    assertJobCreated(
        "foo",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  public static class PayloadObject {
    public String foo;
  }
}
