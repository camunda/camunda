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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public class MessageStartEventSubscriptionMultiplePartitionsTest {
  private static final String MESSAGE_NAME1 = "startMessage1";
  private static final String EVENT_ID1 = "startEventId1";

  public @Rule EngineRule engine = EngineRule.multiplePartition(3);

  @Test
  public void shouldOpenMessageStartEventSubscriptionOnAllPartitions() {
    // when
    engine.deployment().withXmlResource(createWorkflowWithOneMessageStartEvent()).deploy();

    // then
    final List<Record<MessageStartEventSubscriptionRecordValue>> subscriptions =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.OPENED)
            .limit(3)
            .asList();

    assertThat(subscriptions)
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(v -> tuple(v.getStartEventId(), v.getMessageName()))
        .containsOnly(tuple(EVENT_ID1, MESSAGE_NAME1));

    final List<Integer> partitionIds = engine.getPartitionIds();
    assertThat(subscriptions)
        .extracting(Record::getPartitionId)
        .containsExactlyInAnyOrderElementsOf(partitionIds);
  }

  private static BpmnModelInstance createWorkflowWithOneMessageStartEvent() {
    return Bpmn.createExecutableProcess("processId")
        .startEvent(EVENT_ID1)
        .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
        .endEvent()
        .done();
  }
}
