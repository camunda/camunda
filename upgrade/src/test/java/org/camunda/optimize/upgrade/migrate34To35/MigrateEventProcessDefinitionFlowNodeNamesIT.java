/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate34To35;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventProcessDefinitionDto;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade34to35PlanFactory;
import org.camunda.optimize.upgrade.migrate34To35.indices.EventProcessDefinitionIndexV3Old;
import org.elasticsearch.search.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateEventProcessDefinitionFlowNodeNamesIT extends AbstractUpgrade34IT {

  @SneakyThrows
  @Test
  public void migrateEventProcessDefinitionAddFlowNodeDataFieldAndDeleteFlowNodeNamesField() {
    // given
    executeBulk("steps/3.4/definition/34-event-process-definition.json");
    final UpgradePlan upgradePlan = new Upgrade34to35PlanFactory().createUpgradePlan(upgradeDependencies);

    List<FlowNodeDataDto> instanceWithMixedFlowNodesExpectedFlowNode = Arrays.asList(
      new FlowNodeDataDto("invoice_approved", "Invoice approved?", "exclusiveGateway"),
      new FlowNodeDataDto("approveInvoice", "Approve Invoice", "userTask"),
      new FlowNodeDataDto("StartEvent_1", "Invoice received", "startEvent"),
      new FlowNodeDataDto("reviewInvoice", "Review Invoice", "callActivity"),
      new FlowNodeDataDto("assignApprover", "Assign Approver Group", "businessRuleTask"),
      new FlowNodeDataDto("ServiceTask_06mdb3v", "Notify Creditor", "serviceTask"),
      new FlowNodeDataDto("invoiceNotProcessed", "Invoice not processed", "endEvent"),
      new FlowNodeDataDto("reviewSuccessful_gw", "Review successful?", "exclusiveGateway"),
      new FlowNodeDataDto("invoiceProcessed", "Invoice processed", "endEvent"),
      new FlowNodeDataDto("ServiceTask_1", "Archive Invoice", "serviceTask"),
      new FlowNodeDataDto("prepareBankTransfer", "Prepare Bank Transfer", "userTask")
    );

    List<FlowNodeDataDto> instanceWithFlowNodesWithSameName = Arrays.asList(
      new FlowNodeDataDto("Event_1gmsgb4", "someEndEvent_5", "endEvent"),
      new FlowNodeDataDto("Event_1u1cche", "someEvent_5", "intermediateThrowEvent"),
      new FlowNodeDataDto("Event_1i7z1z1", "someEndEvent_3", "endEvent"),
      new FlowNodeDataDto("Event_018144e", "someEndEvent_1", "endEvent"),
      new FlowNodeDataDto("Event_05596pq", null, "startEvent"),
      new FlowNodeDataDto("Event_1skaecd", "someEvent_1", "intermediateThrowEvent"),
      new FlowNodeDataDto("Event_0d17rvo", "someEndEvent", "endEvent"),
      new FlowNodeDataDto("Event_0efrnoi", "someEndEvent_2", "endEvent"),
      new FlowNodeDataDto("Event_032u0a4", null, "intermediateThrowEvent"),
      new FlowNodeDataDto("Event_0t1at4t", "someEvent_3", "intermediateCatchEvent"),
      new FlowNodeDataDto("Event_1fbwecg", "someEvent_7", "intermediateCatchEvent"),
      new FlowNodeDataDto("Event_1shc4fh", "someStartEvent_1", "startEvent"),
      new FlowNodeDataDto("Event_1v2b14x", "someEvent_7", "intermediateThrowEvent"),
      new FlowNodeDataDto("Event_0h3y6qb", "someEvent_4", "intermediateThrowEvent"),
      new FlowNodeDataDto("Event_03j5b0z", "someEndEvent_4", "endEvent"),
      new FlowNodeDataDto("StartEvent_1", "someStartEvent", "startEvent"),
      new FlowNodeDataDto("Event_0n68i8i", "someEvent_2", "intermediateCatchEvent"),
      new FlowNodeDataDto("Event_1tqbmgs", "someStartEvent", "startEvent"),
      new FlowNodeDataDto("Event_08m9fe6", "someEvent_1", "intermediateCatchEvent"),
      new FlowNodeDataDto("Event_18vps6g", "somEvent_1", "endEvent"),
      new FlowNodeDataDto("Event_07zeb8r", "someStartEvent_2", "startEvent")
    );

    List<FlowNodeDataDto> instanceWithEmptyFlowNodesExpectedFlowNode = new ArrayList<>();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    //then
    final EventProcessDefinitionIndex newIndex = new EventProcessDefinitionIndex();
    assertThat(indexExists(new EventProcessDefinitionIndexV3Old())).isFalse();
    assertThat(indexExists(newIndex)).isTrue();
    assertThat(getAllDocumentsOfIndexAs(
      newIndex.getIndexName(),
      EventProcessDefinitionDto.class
    )).extracting(EventProcessDefinitionDto::getFlowNodeData)
      .contains(
        instanceWithMixedFlowNodesExpectedFlowNode,
        instanceWithFlowNodesWithSameName,
        instanceWithEmptyFlowNodesExpectedFlowNode,
        // The definitions with null xml will have no flow node data
        new ArrayList<>()
      );
    assertThat(getAllDocumentsOfIndex(newIndex.getIndexName()))
      .hasSize(1003)
      .allSatisfy(this::assertFlowNodeNamesFieldHasBeenRemoved);
  }

  private void assertFlowNodeNamesFieldHasBeenRemoved(final SearchHit eventProcessDefinition) {
    assertThat(eventProcessDefinition.getSourceAsMap()).doesNotContainKey("flowNodeNames");
  }
}
