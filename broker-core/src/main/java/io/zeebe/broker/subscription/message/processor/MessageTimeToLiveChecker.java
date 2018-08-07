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
package io.zeebe.broker.subscription.message.processor;

import static io.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.broker.logstreams.processor.TypedCommandWriter;
import io.zeebe.broker.subscription.message.data.MessageRecord;
import io.zeebe.broker.subscription.message.state.MessageDataStore;
import io.zeebe.broker.subscription.message.state.MessageDataStore.Message;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.List;

public class MessageTimeToLiveChecker implements Runnable {

  private final TypedCommandWriter writer;
  private final MessageDataStore messageStore;

  private final MessageRecord deleteMessageCommand = new MessageRecord();

  public MessageTimeToLiveChecker(TypedCommandWriter writer, MessageDataStore messageStore) {
    this.writer = writer;
    this.messageStore = messageStore;
  }

  @Override
  public void run() {
    final List<Message> messages =
        messageStore.findMessagesWithDeadlineBefore(ActorClock.currentTimeMillis());

    messages.forEach(this::writeDeleteMessageCommand);
  }

  private void writeDeleteMessageCommand(Message message) {
    deleteMessageCommand.reset();
    deleteMessageCommand
        .setName(wrapString(message.getName()))
        .setCorrelationKey(wrapString(message.getCorrelationKey()))
        .setTimeToLive(message.getTimeToLive())
        .setPayload(wrapArray(message.getPayload()));

    if (message.getId() != null) {
      deleteMessageCommand.setMessageId(wrapString(message.getId()));
    }

    writer.writeFollowUpCommand(message.getKey(), MessageIntent.DELETE, deleteMessageCommand);
    writer.flush();
  }
}
