/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.engine.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.test.MsgPackConstants;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CompleteJobTest {
  private static final String JOB_TYPE = "foo";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  private PartitionTestClient testClient;

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Before
  public void setUp() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCompleteJob() {
    // given
    createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = receiveSingleJobEvent();

    // when
    final JobRecordValue jobEventValue = jobEvent.getValue();
    final ExecuteCommandResponse response =
        testClient.completeJob(jobEvent.getKey(), jobEventValue.getVariables());

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);

    assertThat(response.getValue())
        .contains(
            entry("worker", jobEventValue.getWorker()),
            entry("type", jobEventValue.getType()),
            entry("retries", (long) jobEventValue.getRetries()),
            entry("deadline", jobEventValue.getDeadline().toEpochMilli()));

    final Record<JobRecordValue> loggedEvent =
        RecordingExporter.jobRecords(JobIntent.COMPLETED).getFirst();

    assertThat(loggedEvent.getValue().getType()).isEqualTo(JOB_TYPE);
  }

  @Test
  public void shouldRejectCompletionIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final ExecuteCommandResponse response =
        testClient.completeJob(key, MsgPackConstants.MSGPACK_VARIABLES);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETE);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldCompleteJobWithVariables() {
    // given
    createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = receiveSingleJobEvent();

    // when
    final ExecuteCommandResponse response =
        testClient.completeJob(jobEvent.getKey(), MsgPackConstants.MSGPACK_VARIABLES);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.getValue())
        .contains(entry("variables", MsgPackConstants.MSGPACK_VARIABLES));
  }

  @Test
  public void shouldCompleteJobWithNilVariables() {
    // given
    createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();
    final Record<JobRecordValue> jobEvent = receiveSingleJobEvent();

    // when
    final ExecuteCommandResponse response =
        testClient.completeJob(jobEvent.getKey(), MsgPackHelper.NIL);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.getValue()).contains(entry("variables", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldCompleteJobWithZeroLengthVariables() {
    // given
    createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = receiveSingleJobEvent();

    // when
    final ExecuteCommandResponse response = testClient.completeJob(jobEvent.getKey(), new byte[0]);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.getValue()).contains(entry("variables", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldCompleteJobWithNoVariables() {
    // given
    createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = receiveSingleJobEvent();

    // when
    final ExecuteCommandResponse response =
        testClient.completeJob(jobEvent.getKey(), jobEvent.getValue().getVariables());

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.getValue()).contains(entry("variables", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldThrowExceptionOnCompletionIfVariablesAreInvalid() {
    // given
    createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = receiveSingleJobEvent();
    final byte[] invalidVariables = new byte[] {1}; // positive fixnum, i.e. no object

    // when
    final Throwable throwable =
        catchThrowable(() -> testClient.completeJob(jobEvent.getKey(), invalidVariables));

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Could not read property 'variables'");
    assertThat(throwable.getMessage())
        .contains("Expected document to be a root level object, but was 'INTEGER'");
  }

  @Test
  public void shouldRejectCompletionIfJobIsCompleted() {
    // given
    createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = receiveSingleJobEvent();
    testClient.completeJob(jobEvent.getKey(), jobEvent.getValue().getVariables());

    // when

    final ExecuteCommandResponse response =
        testClient.completeJob(jobEvent.getKey(), jobEvent.getValue().getVariables());

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETE);
  }

  @Test
  public void shouldRejectCompletionIfJobIsFailed() {
    // given
    createJob(JOB_TYPE);

    // when
    apiRule.activateJobs(JOB_TYPE).await();
    final Record<JobRecordValue> jobEvent = receiveSingleJobEvent();
    failJob(jobEvent.getKey());
    final ExecuteCommandResponse response =
        testClient.completeJob(jobEvent.getKey(), jobEvent.getValue().getVariables());

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETE);
  }

  private long createJob(final String type) {
    return apiRule.partitionClient().createJob(type);
  }

  private void failJob(final long key) {
    apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.FAIL)
        .key(key)
        .command()
        .put("retries", 0)
        .done()
        .sendAndAwait();
  }

  private Record<JobRecordValue> receiveSingleJobEvent() {
    return testClient.receiveFirstJobEvent(JobIntent.ACTIVATED);
  }
}
