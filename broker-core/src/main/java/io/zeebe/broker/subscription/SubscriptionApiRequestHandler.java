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
package io.zeebe.broker.subscription;

import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import org.agrona.DirectBuffer;

public class SubscriptionApiRequestHandler implements ServerMessageHandler {

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  @Override
  public boolean onMessage(
      ServerOutput output,
      RemoteAddress remoteAddress,
      DirectBuffer buffer,
      int offset,
      int length) {

    messageHeaderDecoder.wrap(buffer, offset);

    final int schemaId = messageHeaderDecoder.schemaId();

    if (DummyMessageDecoder.SCHEMA_ID == schemaId) {
      final int templateId = messageHeaderDecoder.templateId();

      switch (templateId) {
        case DummyMessageDecoder.TEMPLATE_ID:
          {
            // TODO handle subscription message
            return true;
          }
        default:
          return true;
      }
    }

    return true;
  }
}
