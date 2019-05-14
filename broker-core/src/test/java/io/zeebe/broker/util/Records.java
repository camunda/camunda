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
package io.zeebe.broker.util;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.Intent;

public class Records {

  public static boolean hasIntent(final LoggedEvent event, final Intent intent) {
    if (event == null) {
      return false;
    }

    final RecordMetadata metadata = getMetadata(event);

    return metadata.getIntent() == intent;
  }

  public static boolean isWorkflowInstanceRecord(final LoggedEvent event) {
    return isRecordOfType(event, ValueType.WORKFLOW_INSTANCE);
  }

  private static RecordMetadata getMetadata(final LoggedEvent event) {
    final RecordMetadata metadata = new RecordMetadata();
    event.readMetadata(metadata);

    return metadata;
  }

  public static boolean isRecordOfType(final LoggedEvent event, final ValueType type) {
    if (event == null) {
      return false;
    }

    final RecordMetadata metadata = getMetadata(event);

    return metadata.getValueType() == type;
  }
}
