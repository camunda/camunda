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
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.TypedCommandWriter;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.util.sched.clock.ActorClock;

public class MessageTimeToLiveChecker implements Runnable {

  private final TypedCommandWriter writer;
  private final MessageState messageState;

  private final MessageRecord deleteMessageCommand = new MessageRecord();

  public MessageTimeToLiveChecker(
      final TypedCommandWriter writer, final MessageState messageState) {
    this.writer = writer;
    this.messageState = messageState;
  }

  @Override
  public void run() {
    messageState.visitMessagesWithDeadlineBefore(
        ActorClock.currentTimeMillis(), this::writeDeleteMessageCommand);
  }

  private boolean writeDeleteMessageCommand(final Message message) {
    deleteMessageCommand.reset();
    deleteMessageCommand
        .setName(message.getName())
        .setCorrelationKey(message.getCorrelationKey())
        .setTimeToLive(message.getTimeToLive())
        .setVariables(message.getVariables());

    if (message.getId() != null) {
      deleteMessageCommand.setMessageId(message.getId());
    }

    writer.appendFollowUpCommand(message.getKey(), MessageIntent.DELETE, deleteMessageCommand);

    final long position = writer.flush();
    return position > 0;
  }
}
