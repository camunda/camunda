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
package io.zeebe.broker.subscription.message.state;

import io.zeebe.broker.logstreams.processor.JsonSnapshotSupport;
import io.zeebe.broker.subscription.message.state.MessageDataStore.MessageData;
import java.util.ArrayList;
import java.util.List;

public class MessageDataStore extends JsonSnapshotSupport<MessageData> {

  public MessageDataStore() {
    super(MessageData.class);
  }

  public void addMessage(MessageEntry message) {
    getData().messages.add(message);
  }

  public boolean hasMessage(MessageEntry message) {
    return getData()
        .messages
        .stream()
        .anyMatch(
            m ->
                m.getId() != null
                    && m.getId().equals(message.getId())
                    && m.getName().equals(message.getName())
                    && m.getCorrelationKey().equals(message.getCorrelationKey()));
  }

  public static class MessageData {

    private List<MessageEntry> messages = new ArrayList<>();
  }

  public static class MessageEntry {
    private final String name;
    private final String correlationKey;
    private final byte[] payload;
    private final String id;

    public MessageEntry(String name, String correlationKey, byte[] payload, String id) {
      this.name = name;
      this.correlationKey = correlationKey;
      this.payload = payload;
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public String getCorrelationKey() {
      return correlationKey;
    }

    public byte[] getPayload() {
      return payload;
    }

    public String getId() {
      return id;
    }
  }
}
