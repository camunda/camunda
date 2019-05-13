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

import static io.zeebe.protocol.intent.JobIntent.ACTIVATED;
import static io.zeebe.protocol.intent.JobIntent.COMPLETED;
import static io.zeebe.protocol.intent.JobIntent.FAILED;
import static io.zeebe.protocol.intent.JobIntent.TIME_OUT;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.engine.processor.workflow.job.JobTimeoutTrigger;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class JobTimeOutTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);
  private PartitionTestClient client;

  @Before
  public void setup() {
    client = apiRule.partitionClient();
  }

  @Test
  public void shouldNotTimeOutIfDeadlineNotExceeded() {
    // given
    brokerRule.getClock().pinCurrentTime();
    final Duration timeout = Duration.ofSeconds(60);
    final String jobType = "jobType";

    createJob(jobType);

    apiRule.activateJobs(apiRule.getDefaultPartitionId(), jobType, timeout.toMillis()).await();
    client.receiveFirstJobEvent(ACTIVATED);

    // when
    brokerRule.getClock().addTime(timeout.minus(Duration.ofSeconds(1)));

    // then
    assertNoMoreJobsReceived(ACTIVATED);
  }

  @Test
  public void shouldNotTimeOutIfJobCompleted() {
    // given
    brokerRule.getClock().pinCurrentTime();
    final Duration timeout = Duration.ofSeconds(60);
    final String jobType = "jobType";

    createJob(jobType);

    apiRule.activateJobs(apiRule.getDefaultPartitionId(), jobType, timeout.toMillis()).await();
    final Record activatedJob = client.receiveFirstJobEvent(ACTIVATED);
    client.completeJob(activatedJob.getKey(), "{}");

    // when
    brokerRule.getClock().addTime(timeout.plus(Duration.ofSeconds(1)));

    // then
    assertNoMoreJobsReceived(COMPLETED);
  }

  @Test
  public void shouldNotTimeOutIfJobFailed() {
    // given
    brokerRule.getClock().pinCurrentTime();
    final Duration timeout = Duration.ofSeconds(60);
    final String jobType = "jobType";

    createJob(jobType);

    apiRule.activateJobs(apiRule.getDefaultPartitionId(), jobType, timeout.toMillis()).await();
    final Record activatedJob = client.receiveFirstJobEvent(ACTIVATED);
    client.failJob(activatedJob.getKey(), 0);

    // when
    brokerRule.getClock().addTime(timeout.plus(Duration.ofSeconds(1)));

    // then
    assertNoMoreJobsReceived(FAILED);
  }

  @Test
  public void shouldTimeOutJob() {
    // given
    final String jobType = "foo";
    final long jobKey1 = createJob(jobType);
    final long timeout = 10L;

    apiRule.activateJobs(apiRule.getDefaultPartitionId(), jobType, timeout);
    client.receiveFirstJobEvent(ACTIVATED);
    brokerRule.getClock().addTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);

    // when expired
    client.receiveFirstJobEvent(TIME_OUT);
    apiRule.activateJobs(jobType);

    // then activated again
    final List<Record<JobRecordValue>> jobEvents =
        client.receiveJobs().limit(6).collect(Collectors.toList());

    assertThat(jobEvents).extracting(e -> e.getKey()).contains(jobKey1);
    assertThat(jobEvents)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsExactly(
            JobIntent.CREATE,
            JobIntent.CREATED,
            JobIntent.ACTIVATED,
            JobIntent.TIME_OUT,
            JobIntent.TIMED_OUT,
            JobIntent.ACTIVATED);
  }

  @Test
  public void shouldSetCorrectSourcePositionAfterJobTimeOut() {
    // given
    final String jobType = "foo";
    createJob(jobType);
    final long timeout = 10L;
    apiRule.activateJobs(apiRule.getDefaultPartitionId(), jobType, timeout);
    client.receiveFirstJobEvent(ACTIVATED);
    brokerRule.getClock().addTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);

    // when expired
    client.receiveFirstJobEvent(TIME_OUT);
    apiRule.activateJobs(jobType);

    // then activated again
    final Record jobActivated =
        client
            .receiveJobs()
            .skipUntil(j -> j.getMetadata().getIntent() == TIME_OUT)
            .withIntent(ACTIVATED)
            .getFirst();

    final Record firstActivateCommand =
        client.receiveFirstJobBatchCommands().withIntent(JobBatchIntent.ACTIVATE).getFirst();
    assertThat(jobActivated.getSourceRecordPosition())
        .isNotEqualTo(firstActivateCommand.getPosition());

    final Record secondActivateCommand =
        client
            .receiveFirstJobBatchCommands()
            .withIntent(JobBatchIntent.ACTIVATE)
            .skipUntil(s -> s.getPosition() > firstActivateCommand.getPosition())
            .findFirst()
            .get();

    assertThat(jobActivated.getSourceRecordPosition())
        .isEqualTo(secondActivateCommand.getPosition());
  }

  @Test
  public void shouldExpireMultipleActivatedJobsAtOnce() {
    // given
    final String jobType = "foo";
    final long jobKey1 = createJob(jobType);
    final long jobKey2 = createJob(jobType);
    final long timeout = 10L;

    apiRule.activateJobs(apiRule.getDefaultPartitionId(), jobType, timeout);

    // when
    client.receiveJobs().withIntent(ACTIVATED).limit(2).count();
    brokerRule.getClock().addTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    client.receiveFirstJobEvent(JobIntent.TIMED_OUT);
    apiRule.activateJobs(jobType);

    // then
    final List<Record<JobRecordValue>> activiatedEvents =
        client
            .receiveJobs()
            .filter(e -> e.getMetadata().getIntent() == JobIntent.ACTIVATED)
            .limit(4)
            .collect(Collectors.toList());

    assertThat(activiatedEvents)
        .hasSize(4)
        .extracting(e -> e.getKey())
        .containsExactlyInAnyOrder(jobKey1, jobKey2, jobKey1, jobKey2);

    final List<Record<JobRecordValue>> expiredEvents =
        client
            .receiveJobs()
            .filter(e -> e.getMetadata().getIntent() == JobIntent.TIMED_OUT)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(expiredEvents)
        .extracting(e -> e.getKey())
        .containsExactlyInAnyOrder(jobKey1, jobKey2);
  }

  private long createJob(final String type) {
    return apiRule.partitionClient().createJob(type);
  }

  private void assertNoMoreJobsReceived(Intent lastIntent) {
    final long eventCount =
        client.receiveJobs().limit(j -> j.getMetadata().getIntent() == lastIntent).count();

    assertThat(client.receiveJobs().skip(eventCount).exists()).isFalse();
  }
}
