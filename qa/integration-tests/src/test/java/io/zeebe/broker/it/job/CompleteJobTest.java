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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.ZeebeAssertHelper;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.cmd.ClientException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class CompleteJobTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Rule public Timeout testTimeout = Timeout.seconds(15);

  private ActivatedJob jobEvent;
  private long jobKey;

  @Before
  public void init() {
    clientRule.getJobClient().newCreateCommand().jobType("test").send().join();

    final RecordingJobHandler jobHandler = new RecordingJobHandler();
    clientRule.getJobClient().newWorker().jobType("test").handler(jobHandler).open();

    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());
    jobEvent = jobHandler.getHandledJobs().get(0);
    jobKey = jobEvent.getKey();
  }

  @Test
  public void shouldCompleteJobWithoutPayload() {
    // when
    clientRule.getJobClient().newCompleteCommand(jobKey).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{}");
          assertThat(job.getPayloadAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldCompleteJobNullPayload() {
    // when
    clientRule.getJobClient().newCompleteCommand(jobKey).payload("null").send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{}");
          assertThat(job.getPayloadAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldCompleteJobWithPayload() {
    // when
    clientRule.getJobClient().newCompleteCommand(jobKey).payload("{\"foo\":\"bar\"}").send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldThrowExceptionOnCompleteJobWithInvalidPayload() {
    // when
    final Throwable throwable =
        catchThrowable(
            () -> clientRule.getJobClient().newCompleteCommand(jobKey).payload("[]").send().join());

    // then
    assertThat(throwable).isInstanceOf(ClientException.class);
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldCompleteJobWithPayloadAsMap() {
    // when
    clientRule
        .getJobClient()
        .newCompleteCommand(jobKey)
        .payload(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldCompleteJobWithPayloadAsObject() {
    final PayloadObject payload = new PayloadObject();
    payload.foo = "bar";

    // when
    clientRule.getJobClient().newCompleteCommand(jobKey).payload(payload).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldProvideReasonInExceptionMessageOnRejection() {
    // given
    final JobClient jobClient = clientRule.getClient().jobClient();

    final JobEvent job = jobClient.newCreateCommand().jobType("bar").send().join();
    jobClient.newCompleteCommand(job.getKey()).send().join();

    // then
    thrown.expect(ClientException.class);
    thrown.expectMessage(
        "Command (COMPLETE) for event with key "
            + job.getKey()
            + " was rejected. It is not applicable in the current state. "
            + "Job does not exist");

    // when
    jobClient.newCompleteCommand(job.getKey()).send().join();
  }

  public static class PayloadObject {
    public String foo;
  }
}
