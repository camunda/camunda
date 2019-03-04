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
package io.zeebe.broker.workflow.message;

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MessageCorrelationMultiplePartitionsTest {

  private static final String CORRELATION_KEY_PARTITION_0 = "item-2";
  private static final String CORRELATION_KEY_PARTITION_1 = "item-1";
  private static final String CORRELATION_KEY_PARTITION_2 = "item-0";

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKey("$.key"))
          .endEvent("end")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    assertThat(getPartitionId(CORRELATION_KEY_PARTITION_0)).isEqualTo(START_PARTITION_ID);
    assertThat(getPartitionId(CORRELATION_KEY_PARTITION_1)).isEqualTo(START_PARTITION_ID + 1);
    assertThat(getPartitionId(CORRELATION_KEY_PARTITION_2)).isEqualTo(START_PARTITION_ID + 2);

    testClient = apiRule.partitionClient();

    testClient.deploy(WORKFLOW);
  }

  @Test
  public void shouldOpenMessageSubscriptionsOnDifferentPartitions() {
    // when
    IntStream.range(0, 10)
        .forEach(
            i -> {
              testClient.createWorkflowInstance(
                  PROCESS_ID, asMsgPack("key", CORRELATION_KEY_PARTITION_0));
              testClient.createWorkflowInstance(
                  PROCESS_ID, asMsgPack("key", CORRELATION_KEY_PARTITION_1));
              testClient.createWorkflowInstance(
                  PROCESS_ID, asMsgPack("key", CORRELATION_KEY_PARTITION_2));
            });

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED)
                .limit(30))
        .extracting(r -> tuple(r.getMetadata().getPartitionId(), r.getValue().getCorrelationKey()))
        .containsOnly(
            tuple(START_PARTITION_ID, CORRELATION_KEY_PARTITION_0),
            tuple(START_PARTITION_ID + 1, CORRELATION_KEY_PARTITION_1),
            tuple(START_PARTITION_ID + 2, CORRELATION_KEY_PARTITION_2));
  }

  @Test
  public void shouldCorrelateMessageOnDifferentPartitions() {
    // given
    apiRule
        .partitionClient(START_PARTITION_ID)
        .publishMessage("message", CORRELATION_KEY_PARTITION_0, asMsgPack("p", "p0"));
    apiRule
        .partitionClient(START_PARTITION_ID + 1)
        .publishMessage("message", CORRELATION_KEY_PARTITION_1, asMsgPack("p", "p1"));
    apiRule
        .partitionClient(START_PARTITION_ID + 2)
        .publishMessage("message", CORRELATION_KEY_PARTITION_2, asMsgPack("p", "p2"));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", CORRELATION_KEY_PARTITION_0));
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", CORRELATION_KEY_PARTITION_1));
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", CORRELATION_KEY_PARTITION_2));

    // then
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId("receive-message")
            .limit(3)
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(r -> r.getValue().getPayloadAsMap().get("p"))
        .contains("p0", "p1", "p2");
  }

  private int getPartitionId(final String correlationKey) {
    final List<Integer> partitionIds = apiRule.getPartitionIds();
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(correlationKey), partitionIds.size());
  }
}
