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

import static io.zeebe.broker.it.util.StatusCodeMatcher.hasStatusCode;
import static io.zeebe.broker.it.util.StatusDescriptionMatcher.descriptionContains;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.grpc.Status.Code;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.ZeebeAssertHelper;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.cmd.ClientStatusException;
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
    clientRule.createSingleJob("test");

    final RecordingJobHandler jobHandler = new RecordingJobHandler();
    clientRule.getClient().newWorker().jobType("test").handler(jobHandler).open();

    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());
    jobEvent = jobHandler.getHandledJobs().get(0);
    jobKey = jobEvent.getKey();
  }

  @Test
  public void shouldCompleteJobWithoutVariables() {
    // when
    clientRule.getClient().newCompleteCommand(jobKey).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getVariables()).isEqualTo("{}");
          assertThat(job.getVariablesAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldCompleteJobNullVariables() {
    // when
    clientRule.getClient().newCompleteCommand(jobKey).variables("null").send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getVariables()).isEqualTo("{}");
          assertThat(job.getVariablesAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldCompleteJobWithVariables() {
    // when
    clientRule.getClient().newCompleteCommand(jobKey).variables("{\"foo\":\"bar\"}").send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getVariables()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getVariablesAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldThrowExceptionOnCompleteJobWithInvalidVariables() {
    // expect
    thrown.expect(ClientStatusException.class);
    thrown.expect(hasStatusCode(Code.INVALID_ARGUMENT));
    thrown.expect(
        descriptionContains(
            "Property 'variables' is invalid: Expected document to be a root level object, but was 'ARRAY'"));

    // when
    clientRule.getClient().newCompleteCommand(jobKey).variables("[]").send().join();
  }

  @Test
  public void shouldCompleteJobWithVariablesAsMap() {
    // when
    clientRule
        .getClient()
        .newCompleteCommand(jobKey)
        .variables(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getVariables()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getVariablesAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldCompleteJobWithVariablesAsObject() {
    final VariablesObject variables = new VariablesObject();
    variables.foo = "bar";

    // when
    clientRule.getClient().newCompleteCommand(jobKey).variables(variables).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        "test",
        (job) -> {
          assertThat(job.getVariables()).isEqualTo("{\"foo\":\"bar\"}");
          assertThat(job.getVariablesAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldProvideReasonInExceptionMessageOnRejection() {
    // given
    final JobClient jobClient = clientRule.getClient();
    final long jobKey = clientRule.createSingleJob("bar");
    jobClient.newCompleteCommand(jobKey).send().join();

    // expect
    thrown.expect(ClientStatusException.class);
    thrown.expect(hasStatusCode(Code.NOT_FOUND));

    // when
    jobClient.newCompleteCommand(jobKey).send().join();
  }

  public static class VariablesObject {
    public String foo;
  }
}
