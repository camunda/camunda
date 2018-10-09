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
package io.zeebe.broker.job;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.test.MsgPackConstants;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestPartitionClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class CompleteJobTest {
  private static final String JOB_TYPE = "foo";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  private TestPartitionClient testClient;

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Before
  public void setUp() {
    testClient = apiRule.partition();
  }

  @Test
  public void shouldCompleteJob() {
    // given
    createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    // when
    final ExecuteCommandResponse response =
        completeJob(subscribedEvent.key(), (byte[]) subscribedEvent.value().get("payload"));

    // then
    final SubscribedRecord completeEvent = testClient.receiveFirstJobCommand(JobIntent.COMPLETE);

    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.sourceRecordPosition()).isEqualTo(completeEvent.position());
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETED);

    final Map<String, Object> expectedValue = new HashMap<>(subscribedEvent.value());
    assertThat(response.getValue()).containsAllEntriesOf(expectedValue);

    final Record<JobRecordValue> loggedEvent =
        RecordingExporter.jobRecords(JobIntent.COMPLETED).getFirst();

    assertThat(loggedEvent.getValue().getType()).isEqualTo(JOB_TYPE);
  }

  @Test
  public void shouldRejectCompletionIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final ExecuteCommandResponse response = completeJob(key, MsgPackConstants.MSGPACK_PAYLOAD);

    // then
    final SubscribedRecord completeEvent = testClient.receiveFirstJobCommand(JobIntent.COMPLETE);

    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETE);
    assertThat(response.sourceRecordPosition()).isEqualTo(completeEvent.position());
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Job does not exist");
  }

  @Test
  public void shouldCompleteJobWithPayload() {
    // given
    createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    // when
    final ExecuteCommandResponse response =
        completeJob(subscribedEvent.key(), MsgPackConstants.MSGPACK_PAYLOAD);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.getValue()).contains(entry("payload", MsgPackConstants.MSGPACK_PAYLOAD));
  }

  @Test
  public void shouldCompleteJobWithNilPayload() {
    // given
    createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    // when
    final ExecuteCommandResponse response = completeJob(subscribedEvent.key(), MsgPackHelper.NIL);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.getValue()).contains(entry("payload", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldCompleteJobWithZeroLengthPayload() {
    // given
    createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    // when
    final ExecuteCommandResponse response = completeJob(subscribedEvent.key(), new byte[0]);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.getValue()).contains(entry("payload", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldCompleteJobWithNoPayload() {
    // given
    createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    // when
    final ExecuteCommandResponse response =
        completeJob(subscribedEvent.key(), (byte[]) subscribedEvent.value().get("payload"));

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETED);
    assertThat(response.getValue()).contains(entry("payload", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldThrowExceptionOnCompletionIfPayloadIsInvalid() {
    // given
    createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();
    final byte[] invalidPayload = new byte[] {1}; // positive fixnum, i.e. no object

    // when
    final Throwable throwable =
        catchThrowable(() -> completeJob(subscribedEvent.key(), invalidPayload));

    // then
    assertThat(throwable).isInstanceOf(RuntimeException.class);
    assertThat(throwable.getMessage()).contains("Could not read property 'payload'.");
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldRejectCompletionIfJobIsCompleted() {
    // given
    final ExecuteCommandResponse response2 = createJob(JOB_TYPE);
    assertThat(response2.recordType()).isEqualTo(RecordType.EVENT);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();
    completeJob(subscribedEvent.key(), (byte[]) subscribedEvent.value().get("payload"));

    // when
    final ExecuteCommandResponse response =
        completeJob(subscribedEvent.key(), (byte[]) subscribedEvent.value().get("payload"));

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Job does not exist");
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETE);
  }

  @Test
  public void shouldRejectCompletionIfJobIsFailed() {
    // given
    final ExecuteCommandResponse job = createJob(JOB_TYPE);
    assertThat(job.recordType()).isEqualTo(RecordType.EVENT);

    // when
    apiRule.openJobSubscription(JOB_TYPE).await();
    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();
    failJob(subscribedEvent.key());
    final ExecuteCommandResponse response =
        completeJob(subscribedEvent.key(), (byte[]) subscribedEvent.value().get("payload"));

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Job is failed and must be resolved first");
    assertThat(response.intent()).isEqualTo(JobIntent.COMPLETE);
  }

  private ExecuteCommandResponse createJob(final String type) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.CREATE)
        .command()
        .put("type", type)
        .put("retries", 3)
        .done()
        .sendAndAwait();
  }

  private ExecuteCommandResponse completeJob(final long key, final byte[] payload) {
    return apiRule
        .createCmdRequest()
        .type(ValueType.JOB, JobIntent.COMPLETE)
        .key(key)
        .command()
        .put("payload", payload)
        .done()
        .sendAndAwait();
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

  private SubscribedRecord receiveSingleSubscribedEvent() {
    waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
    return apiRule.subscribedEvents().findFirst().get();
  }
}
