/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.protocol.clientapi.EventType.NULL_VAL;
import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;

import io.zeebe.client.impl.Topic;
import io.zeebe.client.impl.cmd.AbstractExecuteCmdImpl;
import io.zeebe.protocol.clientapi.EventType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ExecuteCommandTest
{
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldThrowExceptionWhenEventTypeIsNull()
    {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("commandEventType cannot be null");

        // when
        new ClientCommand(null);
    }

    @Test
    public void shouldThrowExceptionWhenEventTypeIsNullVal()
    {
        // then
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("commandEventType cannot be null");

        // when
        new ClientCommand(NULL_VAL);
    }

    @Test
    public void shouldSetValidEventType()
    {
        // when
        final ClientCommand cmd = new ClientCommand(TASK_EVENT);

        // then
        assertThat(cmd.getCommandEventType()).isEqualTo(TASK_EVENT);
    }

    static class ClientCommand extends AbstractExecuteCmdImpl<Void, Void>
    {
        ClientCommand(EventType commandEventType)
        {
            super(null, null, new Topic("test-topic", 0), Void.class, commandEventType);
        }

        @Override
        protected Object writeCommand()
        {
            return null;
        }

        @Override
        protected void reset()
        {
        }

        @Override
        protected Void getResponseValue(long key, Void event)
        {
            return null;
        }

        public EventType getCommandEventType()
        {
            return commandEventType;
        }

        @Override
        protected long getKey()
        {
            return 0;
        }
    }
}
