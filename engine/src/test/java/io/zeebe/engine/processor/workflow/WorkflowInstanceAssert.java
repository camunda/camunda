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
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.TypedEventImpl;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.assertj.core.api.AbstractListAssert;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.util.Lists;

public class WorkflowInstanceAssert
    extends AbstractListAssert<
        WorkflowInstanceAssert,
        List<TypedRecord<WorkflowInstanceRecord>>,
        TypedRecord<WorkflowInstanceRecord>,
        ObjectAssert<TypedRecord<WorkflowInstanceRecord>>> {

  public WorkflowInstanceAssert(List<TypedRecord<WorkflowInstanceRecord>> actual) {
    super(actual, WorkflowInstanceAssert.class);
  }

  @Override
  protected ObjectAssert<TypedRecord<WorkflowInstanceRecord>> toAssert(
      TypedRecord<WorkflowInstanceRecord> value, String description) {
    return new ObjectAssert<>(value).describedAs(description);
  }

  @Override
  protected WorkflowInstanceAssert newAbstractIterableAssert(
      Iterable<? extends TypedRecord<WorkflowInstanceRecord>> iterable) {
    return new WorkflowInstanceAssert(Lists.newArrayList(iterable));
  }

  public static WorkflowInstanceAssert assertThat(
      List<TypedRecord<WorkflowInstanceRecord>> workflowInstanceEvents) {
    return new WorkflowInstanceAssert(workflowInstanceEvents);
  }

  /**
   * Asserts that once an element is in state terminating, no flow-related events in its scope are
   * evaluated anymore
   */
  public WorkflowInstanceAssert doesNotEvaluateFlowAfterTerminatingElement(String elementId) {
    final DirectBuffer elementIdBuffer = BufferUtil.wrapString(elementId);

    final Optional<TypedRecord<WorkflowInstanceRecord>> terminatingRecordOptional =
        actual.stream()
            .filter(
                r ->
                    r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_TERMINATING
                        && elementIdBuffer.equals(r.getValue().getElementId()))
            .findFirst();

    if (!terminatingRecordOptional.isPresent()) {
      failWithMessage(
          "Assumption not met: there is not ELEMENT_TERMINATING record for element %s", elementId);
    }

    final TypedEventImpl terminatingRecord = (TypedEventImpl) terminatingRecordOptional.get();
    final long instanceKey = terminatingRecord.getKey();

    final Long2ObjectHashMap<TypedRecord<WorkflowInstanceRecord>> recordsByPosition =
        new Long2ObjectHashMap<>();
    actual.forEach(r -> recordsByPosition.put(((TypedEventImpl) r).getPosition(), r));

    // - once a terminating record is written, there shall be no record with a greater getPosition
    // that
    //   - was handled (has a follow-up event)
    //   - is in an event in the terminating flow scope
    //   - is a non-terminating event
    final Optional<TypedRecord<WorkflowInstanceRecord>> firstViolatingRecord =
        actual.stream()
            .filter(
                r ->
                    ((TypedEventImpl) r).getSourceEventPosition() > terminatingRecord.getPosition())
            .map(r -> recordsByPosition.get(((TypedEventImpl) r).getSourceEventPosition()))
            .filter(r -> r.getValue().getFlowScopeKey() == instanceKey)
            .filter(r -> isFlowEvaluatingState(r.getMetadata().getIntent()))
            .findFirst();

    if (firstViolatingRecord.isPresent()) {
      failWithMessage(
          "Record %s should not have a follow-up event as the flow scope was terminating at that point",
          firstViolatingRecord.get());
    }

    return this;
  }

  private static boolean isFlowEvaluatingState(Intent state) {
    return state == WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN
        || state == WorkflowInstanceIntent.ELEMENT_COMPLETED
        || state == WorkflowInstanceIntent.ELEMENT_ACTIVATING;
  }
}
