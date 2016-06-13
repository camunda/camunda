package org.camunda.tngp.dispatcher;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public interface FragmentWriter
{
    public abstract int getLength();

    public abstract void write(final MutableDirectBuffer buffer, final int offset);
}
