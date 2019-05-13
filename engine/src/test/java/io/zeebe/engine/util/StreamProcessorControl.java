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
package io.zeebe.engine.util;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import java.util.function.Predicate;

public interface StreamProcessorControl {

  void unblock();

  void blockAfterEvent(Predicate<LoggedEvent> test);

  void blockAfterJobEvent(Predicate<TypedRecord<JobRecord>> test);

  void blockAfterDeploymentEvent(Predicate<TypedRecord<DeploymentRecord>> test);

  void blockAfterWorkflowInstanceRecord(Predicate<TypedRecord<WorkflowInstanceRecord>> test);

  void blockAfterWorkflowInstanceCreationRecord(
      final Predicate<TypedRecord<WorkflowInstanceCreationRecord>> test);

  void blockAfterIncidentEvent(Predicate<TypedRecord<IncidentRecord>> test);

  void blockAfterMessageEvent(Predicate<TypedRecord<MessageRecord>> test);

  void blockAfterMessageSubscriptionEvent(Predicate<TypedRecord<MessageSubscriptionRecord>> test);

  void blockAfterWorkflowInstanceSubscriptionEvent(
      Predicate<TypedRecord<WorkflowInstanceSubscriptionRecord>> test);

  void blockAfterTimerEvent(Predicate<TypedRecord<TimerRecord>> test);

  void blockAfterMessageStartEventSubscriptionRecord(
      final Predicate<TypedRecord<MessageStartEventSubscriptionRecord>> test);

  /**
   * @return true if the event to block on has been processed and the stream processor won't handle
   *     any more events until {@link #unblock()} is called.
   */
  boolean isBlocked();

  void close();

  void start();

  void restart();

  SnapshotController getSnapshotController();
}
