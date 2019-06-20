/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.message;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.data.MapEntry.entry;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.client.WorkflowInstanceClient.WorkflowInstanceCreationClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstances;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MessageCorrelationMultiplePartitionsTest {

  private static final Map<Integer, String> CORRELATION_KEYS =
      Maps.of(
          entry(START_PARTITION_ID, "item-2"),
          entry(START_PARTITION_ID + 1, "item-1"),
          entry(START_PARTITION_ID + 2, "item-0"));

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKey("key"))
          .endEvent("end")
          .done();

  @Rule public EngineRule engine = new EngineRule(3);

  @Before
  public void init() {
    assertThat(getPartitionId(CORRELATION_KEYS.get(START_PARTITION_ID)))
        .isEqualTo(START_PARTITION_ID);
    assertThat(getPartitionId(CORRELATION_KEYS.get(START_PARTITION_ID + 1)))
        .isEqualTo(START_PARTITION_ID + 1);
    assertThat(getPartitionId(CORRELATION_KEYS.get(START_PARTITION_ID + 2)))
        .isEqualTo(START_PARTITION_ID + 2);

    engine.deployment().withXmlResource(WORKFLOW).deploy();
  }

  @Test
  public void shouldOpenMessageSubscriptionsOnDifferentPartitions() {
    // when
    IntStream.range(0, 10)
        .forEach(
            i -> {
              final WorkflowInstanceCreationClient workflowInstanceCreationClient =
                  engine.workflowInstance().ofBpmnProcessId(PROCESS_ID);
              workflowInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
                  .create();
              workflowInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
                  .create();
              workflowInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
                  .create();
            });

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED)
                .limit(30))
        .extracting(r -> tuple(r.getPartitionId(), r.getValue().getCorrelationKey()))
        .containsOnly(
            tuple(START_PARTITION_ID, CORRELATION_KEYS.get(START_PARTITION_ID)),
            tuple(START_PARTITION_ID + 1, CORRELATION_KEYS.get(START_PARTITION_ID + 1)),
            tuple(START_PARTITION_ID + 2, CORRELATION_KEYS.get(START_PARTITION_ID + 2)));
  }

  @Test
  public void shouldCorrelateMessageOnDifferentPartitions() {
    // given
    engine.forEachPartition(
        partitionId ->
            engine
                .message()
                .onPartition(partitionId)
                .withName("message")
                .withCorrelationKey(CORRELATION_KEYS.get(partitionId))
                .withVariables(asMsgPack("p", "p" + partitionId))
                .publish());

    // when
    final WorkflowInstanceCreationClient workflowInstanceCreationClient =
        engine.workflowInstance().ofBpmnProcessId(PROCESS_ID);
    final long wfiKey1 =
        workflowInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
            .create();
    final long wfiKey2 =
        workflowInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
            .create();
    final long wfiKey3 =
        workflowInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
            .create();

    // then
    final List<String> correlatedValues =
        Arrays.asList(
            WorkflowInstances.getCurrentVariables(wfiKey1).get("p"),
            WorkflowInstances.getCurrentVariables(wfiKey2).get("p"),
            WorkflowInstances.getCurrentVariables(wfiKey3).get("p"));

    assertThat(correlatedValues).contains("\"p1\"", "\"p2\"", "\"p3\"");
  }

  private int getPartitionId(final String correlationKey) {
    final List<Integer> partitionIds = engine.getPartitionIds();
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(correlationKey), partitionIds.size());
  }
}
