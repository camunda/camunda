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

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.util.ReflectUtil;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class Records {

  public static DeploymentRecord asDeploymentRecord(final LoggedEvent event) {
    return readValueAs(event, DeploymentRecord.class);
  }

  public static JobRecord asJobRecord(final LoggedEvent event) {
    return readValueAs(event, JobRecord.class);
  }

  protected static <T extends UnpackedObject> T readValueAs(
      final LoggedEvent event, final Class<T> valueClass) {
    final DirectBuffer copy =
        BufferUtil.cloneBuffer(
            event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
    final T valuePojo = ReflectUtil.newInstance(valueClass);
    valuePojo.wrap(copy);
    return valuePojo;
  }

  public static boolean isDeploymentRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.DEPLOYMENT);
  }

  public static boolean isJobRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.JOB);
  }

  public static boolean isIncidentRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.INCIDENT);
  }

  public static boolean isWorkflowInstanceRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.WORKFLOW_INSTANCE);
  }

  public static boolean isMessageRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.MESSAGE);
  }

  public static boolean isMessageSubscriptionRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.MESSAGE_SUBSCRIPTION);
  }

  public static boolean isMessageStartEventSubscriptionRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
  }

  public static boolean isWorkflowInstanceSubscriptionRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
  }

  public static boolean isTimerRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.TIMER);
  }

  public static boolean isWorkflowInstanceCreationRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.WORKFLOW_INSTANCE_CREATION);
  }

  public static boolean isErrorRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.ERROR);
  }

  public static boolean hasIntent(final LoggedEvent event, final Intent intent) {
    if (event == null) {
      return false;
    }

    final RecordMetadata metadata = getMetadata(event);

    return metadata.getIntent() == intent;
  }

  private static RecordMetadata getMetadata(final LoggedEvent event) {
    final RecordMetadata metadata = new RecordMetadata();
    event.readMetadata(metadata);

    return metadata;
  }

  public static boolean isRejection(final LoggedEvent event) {
    final RecordMetadata metadata = getMetadata(event);
    return metadata.getRecordType() == RecordType.COMMAND_REJECTION;
  }

  public static boolean isRejection(
      final LoggedEvent event, final ValueType valueType, final Intent intent) {
    return isRejection(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
  }

  public static boolean isEvent(final LoggedEvent event) {
    final RecordMetadata metadata = getMetadata(event);
    return metadata.getRecordType() == RecordType.EVENT;
  }

  public static boolean isEvent(
      final LoggedEvent event, final ValueType valueType, final Intent intent) {
    return isEvent(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
  }

  public static boolean isCommand(final LoggedEvent event) {
    final RecordMetadata metadata = getMetadata(event);
    return metadata.getRecordType() == RecordType.COMMAND;
  }

  public static boolean isCommand(
      final LoggedEvent event, final ValueType valueType, final Intent intent) {
    return isCommand(event) && isRecordOfType(event, valueType) && hasIntent(event, intent);
  }

  public static boolean isRecordOfType(final LoggedEvent event, final ValueType type) {
    if (event == null) {
      return false;
    }

    final RecordMetadata metadata = getMetadata(event);

    return metadata.getValueType() == type;
  }
}
