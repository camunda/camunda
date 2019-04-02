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
package io.zeebe.broker.exporter.record.value;

import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.broker.exporter.record.RecordValueImpl;
import io.zeebe.exporter.api.record.value.MessageStartEventSubscriptionRecordValue;
import java.util.Objects;

public class MessageStartEventSubscriptionRecordValueImpl extends RecordValueImpl
    implements MessageStartEventSubscriptionRecordValue {

  private long workflowKey;
  private String startEventId;
  private String messageName;

  public MessageStartEventSubscriptionRecordValueImpl(
      ExporterObjectMapper objectMapper,
      long workflowKey,
      String startEventId,
      String messageName) {

    super(objectMapper);

    this.workflowKey = workflowKey;
    this.startEventId = startEventId;
    this.messageName = messageName;
  }

  @Override
  public long getWorkflowKey() {
    return this.workflowKey;
  }

  @Override
  public String getStartEventId() {
    return this.startEventId;
  }

  @Override
  public String getMessageName() {
    return this.messageName;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MessageStartEventSubscriptionRecordValueImpl that =
        (MessageStartEventSubscriptionRecordValueImpl) o;
    return workflowKey == that.workflowKey
        && Objects.equals(messageName, that.messageName)
        && Objects.equals(startEventId, that.startEventId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(workflowKey, startEventId, messageName);
  }

  @Override
  public String toString() {
    return "MessageStartEventSubscriptionRecordValueImpl{"
        + "messageName='"
        + messageName
        + '\''
        + ", workflowKey='"
        + workflowKey
        + '\''
        + ", startEventId="
        + startEventId
        + '}';
  }
}
