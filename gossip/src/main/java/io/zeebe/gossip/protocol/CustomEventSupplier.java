package io.zeebe.gossip.protocol;

import java.util.Iterator;

public interface CustomEventSupplier
{
    int customEventSize();

    Iterator<CustomEvent> customEventViewIterator(int max);

    Iterator<CustomEvent> customEventDrainIterator(int max);
}
