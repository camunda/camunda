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
