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
package io.zeebe.broker.subscription.message.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class MessageRecord extends UnpackedObject {

  private final StringProperty nameProp = new StringProperty("name");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey");

  private final DocumentProperty payloadProp = new DocumentProperty("payload");
  private final StringProperty messageIdProp = new StringProperty("messageId", "");

  public MessageRecord() {
    this.declareProperty(nameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(payloadProp)
        .declareProperty(messageIdProp);
  }

  public DirectBuffer getName() {
    return nameProp.getValue();
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKeyProp.getValue();
  }

  public DirectBuffer getPayload() {
    return payloadProp.getValue();
  }

  public boolean hasMessageId() {
    return messageIdProp.getValue().capacity() > 0;
  }

  public DirectBuffer getMessageId() {
    return messageIdProp.getValue();
  }
}
