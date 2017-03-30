package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.protocol.clientapi.EventType.NULL_VAL;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;

import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.protocol.clientapi.EventType;
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

    class ClientCommand extends AbstractExecuteCmdImpl<Void, Void>
    {
        ClientCommand(EventType commandEventType)
        {
            super(null, null, Void.class, 0, commandEventType);
        }

        @Override
        public void validate()
        {
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
        protected Void getResponseValue(int channelId, long key, Void event)
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
