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
package io.zeebe.broker.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.MessageSubscriptionRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceSubscriptionRecordValue;

public class WorkflowAssert {

  private static final String PROCESS_ID = "process";

  public static void assertWorkflowInstanceRecord(
      long workflowInstanceKey, Record<WorkflowInstanceRecordValue> workflowInstanceCanceledEvent) {
    assertWorkflowInstanceRecord(workflowInstanceKey, PROCESS_ID, workflowInstanceCanceledEvent);
  }

  public static void assertWorkflowInstanceRecord(
      long workflowInstanceKey,
      String elementId,
      Record<WorkflowInstanceRecordValue> workflowInstanceCanceledEvent) {
    Assertions.assertThat(workflowInstanceCanceledEvent.getValue())
        .hasBpmnProcessId(PROCESS_ID)
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId(elementId);
  }

  public static void assertWorkflowInstanceRecord(
      long workflowKey,
      int version,
      long workflowInstanceKey,
      String elementId,
      Record<WorkflowInstanceRecordValue> workflowInstanceCanceledEvent) {
    Assertions.assertThat(workflowInstanceCanceledEvent.getValue())
        .hasBpmnProcessId(PROCESS_ID)
        .hasWorkflowKey(workflowKey)
        .hasVersion(version)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId(elementId);
  }

  public static void assertWorkflowInstanceRecord(
      String bpmnId,
      int version,
      long workflowInstanceKey,
      String elementId,
      Record<WorkflowInstanceRecordValue> workflowInstanceCanceledEvent) {
    Assertions.assertThat(workflowInstanceCanceledEvent.getValue())
        .hasBpmnProcessId(bpmnId)
        .hasVersion(version)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId(elementId);
  }

  public static void assertMessageSubscription(
      long workflowInstanceKey,
      Record catchEventEntered,
      Record<MessageSubscriptionRecordValue> subscription) {
    assertMessageSubscription(workflowInstanceKey, "", catchEventEntered, subscription);
  }

  public static void assertMessageSubscription(
      long workflowInstanceKey,
      String correlationKey,
      Record catchEventEntered,
      Record<MessageSubscriptionRecordValue> subscription) {
    Assertions.assertThat(subscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled")
        .hasCorrelationKey(correlationKey);
  }

  public static void assertWorkflowSubscription(
      long workflowInstanceKey,
      Record catchEventEntered,
      Record<WorkflowInstanceSubscriptionRecordValue> subscription) {
    Assertions.assertThat(subscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled");

    assertThat(subscription.getValue().getVariables()).isEqualTo("{}");
  }

  public static void assertWorkflowSubscription(
      long workflowInstanceKey,
      String variables,
      Record catchEventEntered,
      Record<WorkflowInstanceSubscriptionRecordValue> subscription) {
    Assertions.assertThat(subscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled");

    assertThat(subscription.getValue().getVariables()).isEqualTo(variables);
  }
}
