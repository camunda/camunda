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
import io.zeebe.util.sched.clock.ActorClock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MessageDataStore extends JsonSnapshotSupport<MessageData> {

  public MessageDataStore() {
    super(MessageData.class);
  }

  public void addMessage(Message message) {
    getData().getMessages().add(message);
  }

  public boolean hasMessage(Message message) {
    return getData()
        .getMessages()
        .stream()
        .anyMatch(
            m ->
                m.getId() != null
                    && m.getId().equals(message.getId())
                    && m.getName().equals(message.getName())
                    && m.getCorrelationKey().equals(message.getCorrelationKey()));
  }

  public Message findMessage(String name, String correlationKey) {
    return getData()
        .getMessages()
        .stream()
        .filter(m -> m.getName().equals(name) && m.getCorrelationKey().equals(correlationKey))
        .findFirst()
        .orElse(null);
  }

  public List<Message> findMessagesWithDeadlineBefore(long deadline) {
    return getData()
        .getMessages()
        .stream()
        .filter(m -> m.getDeadline() <= deadline)
        .collect(Collectors.toList());
  }

  public boolean removeMessage(long key) {
    return getData().getMessages().removeIf(m -> m.getKey() == key);
  }

  public static class MessageData {

    private final List<Message> messages = new ArrayList<>();

    public List<Message> getMessages() {
      return messages;
    }
  }

  public static class Message {
    private String name;
    private String correlationKey;
    private byte[] payload;
    private String id;
    private long timeToLive;
    private long deadline;

    private long key;

    /* required for json deserialization */
    public Message() {}

    public Message(String name, String correlationKey, long timeToLive, byte[] payload, String id) {
      this.name = name;
      this.correlationKey = correlationKey;
      this.payload = payload;
      this.id = id;
      this.timeToLive = timeToLive;
      this.deadline = timeToLive + ActorClock.currentTimeMillis();
    }

    public long getKey() {
      return key;
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

    public long getDeadline() {
      return deadline;
    }

    public long getTimeToLive() {
      return timeToLive;
    }

    public void setKey(long key) {
      this.key = key;
    }
  }
}
