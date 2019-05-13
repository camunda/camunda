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
package io.zeebe.broker.engine.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerConfigurator;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.MessageStartEventSubscriptionRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MessageStartEventSubscriptionMultiplePartitionsTest {
  private static final String MESSAGE_NAME1 = "startMessage1";
  private static final String EVENT_ID1 = "startEventId1";

  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(EmbeddedBrokerConfigurator.setPartitionCount(3));

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldOpenMessageStartEventSubscriptionOnAllPartitions() {

    testClient.deploy(createWorkflowWithOneMessageStartEvent());

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

    final List<Integer> partitionIds = apiRule.getPartitionIds();
    assertThat(subscriptions)
        .extracting(r -> r.getMetadata().getPartitionId())
        .containsExactlyInAnyOrderElementsOf(partitionIds);
  }

  private static BpmnModelInstance createWorkflowWithOneMessageStartEvent() {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("processId")
            .startEvent(EVENT_ID1)
            .message(m -> m.name(MESSAGE_NAME1).id("startmsgId"))
            .endEvent()
            .done();

    return modelInstance;
  }
}
