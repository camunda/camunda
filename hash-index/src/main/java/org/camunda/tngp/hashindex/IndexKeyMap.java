package org.camunda.tngp.hashindex;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class IndexKeyMap<K extends IndexKeyHandler>
{
    public static final int DEFAULT_CAPACITY = 100;

    protected final K keyHandler;

    protected final UnsafeBuffer buffer;
    protected static final byte VALID_ENTRY = 1;
    protected int entryLimit;
    protected final int entryLength;

    protected GetVisitor getVisitor = new GetVisitor();
    protected IncrementVisitor incrementVisitor = new IncrementVisitor();
    protected DecrementVisitor decrementVisitor = new DecrementVisitor();

    public IndexKeyMap(K keyHandler)
    {
        this(keyHandler, DEFAULT_CAPACITY);
    }

    public IndexKeyMap(K keyHandler, int capacity)
    {
        this.keyHandler = keyHandler;
        this.entryLength = 1 + keyHandler.getKeyLength() + BitUtil.SIZE_OF_INT; // VALID byte + key + value
        buffer = new UnsafeBuffer(new byte[capacity * entryLength]);
    }

    public int getAtCurrentKey(int defaultValue)
    {
        if (scanToCurrentKey(getVisitor))
        {
            return getVisitor.value;
        }
        else
        {
            return defaultValue;
        }
    }

    protected boolean scanToCurrentKey(EntryVisitor visitor)
    {
        for (int i = 0; i < entryLimit; i++)
        {
            final int entryHeadIndex = i * entryLength;

            final boolean isValidEntry = buffer.getByte(entryHeadIndex) == VALID_ENTRY;
            final boolean isMatch = isValidEntry && keyHandler.keyEquals(buffer, entryHeadIndex + 1);

            visitor.visit(buffer, i, isValidEntry, isMatch);

            if (isMatch)
            {
                return true;
            }
        }

        return false;
    }

    public void incrementAtCurrentKey()
    {
        final boolean entryIncremented = scanToCurrentKey(incrementVisitor);

        if (!entryIncremented)
        {
            final int newEntryIndex;

            if (incrementVisitor.firstInvalidEntryIndex < 0)
            {
                // append
                newEntryIndex = entryLimit;
            }
            else
            {
                // overwrite invalidated entry
                newEntryIndex = incrementVisitor.firstInvalidEntryIndex;
            }

            writeNewEntry(newEntryIndex);

            if (entryLimit == newEntryIndex)
            {
                entryLimit++;
            }
        }
    }

    protected void writeNewEntry(int index)
    {
        final int tailOffset = index * entryLength;

        buffer.boundsCheck(tailOffset, entryLength);

        buffer.putByte(tailOffset, VALID_ENTRY);
        keyHandler.writeKey(buffer, tailOffset + 1);
        buffer.putInt(tailOffset + 1 + keyHandler.getKeyLength(), 1);
    }

    public void decrementAtCurrentKey()
    {
        // currently ignores the case when no key is found => could also return that
        scanToCurrentKey(decrementVisitor);

    }

    interface EntryVisitor
    {
        void visit(MutableDirectBuffer buffer, int entryOffset, boolean isValidEntry, boolean isMatch);
    }

    class GetVisitor implements EntryVisitor
    {

        int value;

        @Override
        public void visit(MutableDirectBuffer buffer, int entryIndex, boolean isValidEntry, boolean isMatch)
        {
            if (isMatch)
            {
                final int entryOffset = entryIndex * entryLength;
                value = buffer.getInt(entryOffset + 1 + keyHandler.getKeyLength());
            }
        }
    }

    class IncrementVisitor implements EntryVisitor
    {

        protected int firstInvalidEntryIndex = -1;

        @Override
        public void visit(MutableDirectBuffer buffer, int entryIndex, boolean isValidEntry, boolean isMatch)
        {
            if (isMatch)
            {
                final int entryOffset = entryIndex * entryLength;
                final int valueOffset = entryOffset + 1 + keyHandler.getKeyLength();
                final int value = buffer.getInt(valueOffset);
                buffer.putInt(valueOffset, value + 1);
            }
            else if (!isValidEntry)
            {
                if (firstInvalidEntryIndex < 0)
                {
                    firstInvalidEntryIndex = entryIndex;
                }
            }
        }
    }

    class DecrementVisitor implements EntryVisitor
    {

        @Override
        public void visit(MutableDirectBuffer buffer, int entryIndex, boolean isValidEntry, boolean isMatch)
        {
            if (isMatch)
            {
                final int entryOffset = entryIndex * entryLength;
                final int valueOffset = entryOffset + 1 + keyHandler.getKeyLength();
                final int value = buffer.getInt(valueOffset);
                if (value == 1)
                {
                    // invalidates entry
                    buffer.putByte(entryOffset, (byte) 0);
                }
                else
                {
                    buffer.putInt(valueOffset, value - 1);
                }
            }
        }
    }
}
