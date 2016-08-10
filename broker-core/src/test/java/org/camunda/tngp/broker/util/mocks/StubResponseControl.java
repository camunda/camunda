package org.camunda.tngp.broker.util.mocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class StubResponseControl implements ResponseControl
{
    protected BufferWriterResultCollector values = new BufferWriterResultCollector();
    protected List<Boolean> interactions = new ArrayList<>(); // true => acceptance; false => rejection

    @Override
    public void accept(BufferWriter responseWriter)
    {
        values.add(responseWriter);
        interactions.add(true);
    }

    @Override
    public void reject(ErrorWriter errorWriter)
    {
        values.add(errorWriter);
        interactions.add(false);
    }

    public int size()
    {
        return values.size();
    }

    public <T extends BufferReader> T getRejectionValueAs(int index, Class<T> bufferReaderClass)
    {
        assertThat(interactions.get(index)).isFalse();
        return values.getEntryAs(index, bufferReaderClass);
    }

    public <T extends BufferReader> T getAcceptanceValueAs(int index, Class<T> bufferReaderClass)
    {
        assertThat(interactions.get(index)).isTrue();
        return values.getEntryAs(index, bufferReaderClass);
    }

    public boolean isAcceptance(int index)
    {
        return interactions.get(index);
    }

    public boolean isRejection(int index)
    {
        return !interactions.get(index);
    }



}
