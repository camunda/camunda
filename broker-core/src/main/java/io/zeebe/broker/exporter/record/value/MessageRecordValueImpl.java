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
import io.zeebe.broker.exporter.record.RecordValueWithVariablesImpl;
import io.zeebe.exporter.api.record.value.MessageRecordValue;
import java.util.Objects;

public class MessageRecordValueImpl extends RecordValueWithVariablesImpl
    implements MessageRecordValue {
  private final String name;
  private final String messageId;
  private final String correlationKey;
  private final long timeToLive;

  public MessageRecordValueImpl(
      final ExporterObjectMapper objectMapper,
      final String variables,
      final String name,
      final String messageId,
      final String correlationKey,
      final long timeToLive) {
    super(objectMapper, variables);
    this.name = name;
    this.messageId = messageId;
    this.correlationKey = correlationKey;
    this.timeToLive = timeToLive;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getMessageId() {
    return messageId;
  }

  @Override
  public String getCorrelationKey() {
    return correlationKey;
  }

  @Override
  public long getTimeToLive() {
    return timeToLive;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final MessageRecordValueImpl that = (MessageRecordValueImpl) o;
    return timeToLive == that.timeToLive
        && Objects.equals(name, that.name)
        && Objects.equals(messageId, that.messageId)
        && Objects.equals(correlationKey, that.correlationKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, messageId, correlationKey, timeToLive);
  }

  @Override
  public String toString() {
    return "MessageRecordValueImpl{"
        + "name='"
        + name
        + '\''
        + ", messageId='"
        + messageId
        + '\''
        + ", correlationKey='"
        + correlationKey
        + '\''
        + ", timeToLive="
        + timeToLive
        + ", variables='"
        + variables
        + '\''
        + '}';
  }
}
