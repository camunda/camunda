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
package io.zeebe.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.commands.JobCommand;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.record.JobRecord;
import io.zeebe.client.api.subscription.JobCommandHandler;
import io.zeebe.client.api.subscription.JobEventHandler;
import io.zeebe.test.util.TestUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class JobTopicSubscriptionTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public Timeout timeout = Timeout.seconds(20);

  protected TopicClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient().topicClient();
  }

  @Test
  public void shouldReceiveJobPOJORecords() {
    // given
    client
        .jobClient()
        .newCreateCommand()
        .jobType("foo")
        .addCustomHeader("key", "value")
        .payload("{}")
        .retries(2)
        .send()
        .join();

    final RecordingJobPOJOEventHandler handler = new RecordingJobPOJOEventHandler();

    // when
    client
        .newSubscription()
        .name("sub-1")
        .jobEventHandler(handler)
        .jobCommandHandler(handler)
        .startAtHeadOfTopic()
        .open();

    // then
    TestUtil.waitUntil(() -> handler.numRecords() == 2);

    final JobRecord record1 = handler.getRecord(0);
    assertThat(record1.getMetadata().getIntent()).isEqualTo("CREATE");
    assertThat(record1.getHeaders()).isEmpty();
    assertThat(record1.getCustomHeaders()).containsExactly(entry("key", "value"));
    assertThat(record1.getDeadline()).isNull();
    assertThat(record1.getWorker()).isNull();
    assertThat(record1.getRetries()).isEqualTo(2);
    assertThat(record1.getType()).isEqualTo("foo");
    assertThat(record1.getPayload()).isEqualTo("{}");

    final JobRecord record2 = handler.getRecord(1);
    assertThat(record2.getMetadata().getIntent()).isEqualTo("CREATED");
  }

  @Test
  public void shouldInvokeDefaultHandler() throws IOException {
    // given
    final JobEvent job =
        client
            .jobClient()
            .newCreateCommand()
            .jobType("foo")
            .addCustomHeader("key", "value")
            .payload("{}")
            .send()
            .join();

    final RecordingEventHandler handler = new RecordingEventHandler();

    // when no POJO handler is registered
    client.newSubscription().name("sub-2").recordHandler(handler).startAtHeadOfTopic().open();

    // then
    TestUtil.waitUntil(() -> handler.numJobRecords() == 2);

    final long jobKey = job.getKey();
    handler.assertJobRecord(0, jobKey, "CREATE");
    handler.assertJobRecord(1, jobKey, "CREATED");
  }

  protected static class RecordingJobPOJOEventHandler
      implements JobEventHandler, JobCommandHandler {
    protected List<JobRecord> records = new ArrayList<>();

    @Override
    public void onJobEvent(JobEvent event) {
      this.records.add(event);
    }

    @Override
    public void onJobCommand(JobCommand jobCommand) {
      this.records.add(jobCommand);
    }

    @Override
    public void onJobCommandRejection(JobCommand jobCommand) {
      this.records.add(jobCommand);
    }

    public JobRecord getRecord(int index) {
      return records.get(index);
    }

    public int numRecords() {
      return records.size();
    }
  }
}
