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

import io.zeebe.broker.logstreams.processor.TypedCommandWriter;
import io.zeebe.broker.subscription.message.data.MessageRecord;
import io.zeebe.broker.subscription.message.state.Message;
import io.zeebe.broker.subscription.message.state.MessageStateController;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.List;

public class MessageTimeToLiveChecker implements Runnable {

  private final TypedCommandWriter writer;
  private final MessageStateController messageStateController;

  private final MessageRecord deleteMessageCommand = new MessageRecord();

  public MessageTimeToLiveChecker(
      TypedCommandWriter writer, MessageStateController messageStateController) {
    this.writer = writer;
    this.messageStateController = messageStateController;
  }

  @Override
  public void run() {
    final List<Message> messages =
        messageStateController.findMessageBefore(ActorClock.currentTimeMillis());

    for (Message message : messages) {
      final boolean success = writeDeleteMessageCommand(message);
      if (!success) {
        return;
      }
    }
  }

  private boolean writeDeleteMessageCommand(Message message) {
    deleteMessageCommand.reset();
    deleteMessageCommand
        .setName(message.getName())
        .setCorrelationKey(message.getCorrelationKey())
        .setTimeToLive(message.getTimeToLive())
        .setPayload(message.getPayload());

    if (message.getId() != null) {
      deleteMessageCommand.setMessageId(message.getId());
    }

    writer.writeFollowUpCommand(message.getKey(), MessageIntent.DELETE, deleteMessageCommand);

    final long position = writer.flush();
    return position > 0;
  }
}
