package org.camunda.tngp.list;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.tngp.list.CompactListDescriptor.elementDataOffset;
import static org.camunda.tngp.list.CompactListDescriptor.elementOffset;
import static org.camunda.tngp.list.CompactListDescriptor.framedLength;
import static org.camunda.tngp.list.CompactListDescriptor.requiredBufferCapacity;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.NoSuchElementException;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

public class CompactListTest
{
    CompactList list;

    UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);
    UnsafeBuffer readBuffer = new UnsafeBuffer(0, 0);

    @Before
    public void setup()
    {
        list = new CompactList(SIZE_OF_INT, 16, (bufferCapacity) -> ByteBuffer.allocateDirect(bufferCapacity));
        writeBuffer.wrap(new byte[SIZE_OF_INT]);
        readBuffer.wrap(new byte[SIZE_OF_INT]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldExceptionNotEnoughCapacity()
    {
        final ByteBuffer underlyingBuffer = ByteBuffer.allocateDirect(0);

        new CompactList(underlyingBuffer, 16, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldExceptionForElementLengthTooLargeWhenAddingElement()
    {
        // given
        final UnsafeBuffer element = new UnsafeBuffer(new byte[SIZE_OF_LONG]);

        list.add(element);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldExceptionWhenIndexIsOutOfBoundWhenAddingElement()
    {
        final UnsafeBuffer element = new UnsafeBuffer(new byte[SIZE_OF_INT]);

        try
        {
            list.add(element, 0, SIZE_OF_INT, -1);
            fail("Expected exception: IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException e)
        {
        }

        list.add(element, 0, SIZE_OF_INT, 1);
    }

    @Test
    public void shouldAddElement()
    {
        // given
        writeBuffer.putInt(0, 7);

        // when
        list.add(writeBuffer);

        // then
        assertThat(list.size()).isEqualTo(1);

        list.get(0, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(7);
    }

    @Test
    public void shouldAddElementAtIndex()
    {
        // given
        addValues();

        writeBuffer.putInt(0, 11);

        // assume
        list.get(6, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(6);

        // when
        list.add(writeBuffer, 0, SIZE_OF_INT, 5);

        // then
        assertThat(list.size()).isEqualTo(11);

        list.get(5, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(11);

        // should shift other elements with +1
        list.get(6, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(5);

        list.get(7, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(6);

        list.get(8, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(7);

        list.get(9, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(8);

        list.get(10, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldExceptionForElementLengthTooLargeWhenSettingElement()
    {
        final UnsafeBuffer element = new UnsafeBuffer(new byte[SIZE_OF_LONG]);

        list.set(0, element);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldExceptionWhenIndexIsOutOfBoundWhenSettingElement()
    {
        final UnsafeBuffer element = new UnsafeBuffer(new byte[SIZE_OF_INT]);

        try
        {
            list.set(-1, element);
            fail("Expected exception: IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException e)
        {
        }

        list.set(0, element);
    }

    @Test
    public void shouldSetElement()
    {
        // given
        writeBuffer.putInt(0, 7);
        list.add(writeBuffer);

        writeBuffer.putInt(0, 10);

        // when
        list.set(0, writeBuffer);

        // then
        assertThat(list.size()).isEqualTo(1);

        list.get(0, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(10);
    }

    @Test
    public void shouldSetElementAtIndex()
    {
        // given
        addValues();

        writeBuffer.putInt(0, 11);

        // assume
        list.get(5, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(5);

        // when
        list.set(5, writeBuffer);

        // then
        assertThat(list.size()).isEqualTo(10);

        list.get(5, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(11);

        // should shift other elements with +1
        list.get(6, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(6);

        list.get(7, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(7);

        list.get(8, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(8);

        list.get(9, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(9);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldExceptionWhenIndexIsOutOfBoundWhenRemovingElement()
    {
        try
        {
            list.remove(-1);
            fail("Expected exception: IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException e)
        {
        }

        list.remove(0);
    }

    @Test
    public void shouldRemoveElement()
    {
        // given
        writeBuffer.putInt(0, 7);
        list.add(writeBuffer);

        // when
        list.remove(0);

        // then
        assertThat(list.size()).isEqualTo(0);
    }

    @Test
    public void shouldRemoveElementAtIndex()
    {
        // given
        addValues();

        writeBuffer.putInt(0, 11);

        // assume
        list.get(5, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(5);

        // when
        list.remove(5);

        // then
        assertThat(list.size()).isEqualTo(9);

        list.get(5, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(6);

        list.get(6, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(7);

        list.get(7, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(8);

        list.get(8, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(9);
    }

    @Test
    public void shouldClearList()
    {
        // given
        addValues();

        // assume
        assertThat(list.size()).isEqualTo(10);

        // when
        list.clear();

        // then
        assertThat(list.size()).isEqualTo(0);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldExceptionIndexOutOfBoundWhenGettingElement()
    {
        try
        {
            list.get(-1, readBuffer, 0);
            fail("Expected exception: IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException e)
        {
        }

        list.get(0, readBuffer, 0);
    }

    @Test
    public void shouldGetElement()
    {
        // given
        addValues();

        // when
        list.get(7, readBuffer, 0);

        // then
        assertThat(readBuffer.getInt(0)).isEqualTo(7);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void shouldExceptionIndexOutOfBoundWhenWrappingElement()
    {
        try
        {
            list.wrap(-1, readBuffer);
            fail("Expected exception: IndexOutOfBoundsException");
        }
        catch (IndexOutOfBoundsException e)
        {
        }

        list.wrap(0, readBuffer);
    }

    @Test
    public void shouldWrapElement()
    {
        // given
        addValues();

        // when
        list.wrap(7, readBuffer);

        // then
        assertThat(readBuffer.getInt(0)).isEqualTo(7);
    }

    @Test
    public void shouldFindElement()
    {
        // given
        addValues();

        final UnsafeBuffer keyBuffer = new UnsafeBuffer(new byte[SIZE_OF_INT]);
        keyBuffer.putInt(0, 7);

        // when
        final int idx = list.find(keyBuffer, new Comparator<MutableDirectBuffer>()
        {
            @Override
            public int compare(MutableDirectBuffer o1, MutableDirectBuffer o2)
            {
                return Integer.compare(o1.getInt(0), o2.getInt(0));
            }
        });

        // then
        assertThat(idx).isEqualTo(7);
    }

    @Test
    public void shouldReturnIndexToAdd()
    {
        // given
        writeBuffer.putInt(0, 0);
        list.add(writeBuffer);

        writeBuffer.putInt(0, 2);
        list.add(writeBuffer);

        writeBuffer.putInt(0, 4);
        list.add(writeBuffer);

        writeBuffer.putInt(0, 6);
        list.add(writeBuffer);

        writeBuffer.putInt(0, 8);
        list.add(writeBuffer);

        writeBuffer.putInt(0, 10);
        list.add(writeBuffer);

        final UnsafeBuffer keyBuffer = new UnsafeBuffer(new byte[SIZE_OF_INT]);
        keyBuffer.putInt(0, 7);

        // when
        final int idx = list.find(keyBuffer, new Comparator<MutableDirectBuffer>()
        {
            @Override
            public int compare(MutableDirectBuffer o1, MutableDirectBuffer o2)
            {
                return Integer.compare(o1.getInt(0), o2.getInt(0));
            }
        });

        // then
        assertThat(idx).isEqualTo(-5);
        assertThat(~idx).isEqualTo(4);
    }

    @Test
    public void shouldReturnSize()
    {
        // given
        addValues();

        // when
        final int size = list.size();

        // then
        assertThat(size).isEqualTo(10);
    }

    @Test
    public void shouldReturnMaxElementLength()
    {
        // given

        // when
        final int maxElementDataLength = list.maxElementDataLength();

        // then
        assertThat(maxElementDataLength).isEqualTo(SIZE_OF_INT);
    }

    @Test
    public void shouldCopyInto()
    {
        // given
        addValues();

        final int framedElementLength = framedLength(SIZE_OF_INT);
        final UnsafeBuffer copy = new UnsafeBuffer(new byte[requiredBufferCapacity(framedElementLength, 16)]);

        // when
        list.copyInto(copy, 0);

        // then
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 0)))).isEqualTo(0);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 1)))).isEqualTo(1);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 2)))).isEqualTo(2);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 3)))).isEqualTo(3);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 4)))).isEqualTo(4);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 5)))).isEqualTo(5);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 6)))).isEqualTo(6);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 7)))).isEqualTo(7);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 8)))).isEqualTo(8);
        assertThat(copy.getInt(elementDataOffset(elementOffset(framedElementLength, 9)))).isEqualTo(9);
    }

    @Test
    public void shouldIterate()
    {
        // given
        addValues();

        // when
        final CompactListIterator iterator = list.iterator();

        // then
        assertThat(iterator.next().getInt(0)).isEqualTo(0);
        assertThat(iterator.next().getInt(0)).isEqualTo(1);
        assertThat(iterator.next().getInt(0)).isEqualTo(2);
        assertThat(iterator.next().getInt(0)).isEqualTo(3);
        assertThat(iterator.next().getInt(0)).isEqualTo(4);
        assertThat(iterator.next().getInt(0)).isEqualTo(5);
        assertThat(iterator.next().getInt(0)).isEqualTo(6);
        assertThat(iterator.next().getInt(0)).isEqualTo(7);
        assertThat(iterator.next().getInt(0)).isEqualTo(8);
        assertThat(iterator.next().getInt(0)).isEqualTo(9);
    }

    @Test(expected = NoSuchElementException.class)
    public void shouldExceptionNoSuchElement()
    {
        final CompactListIterator iterator = list.iterator();
        iterator.next();
    }

    @Test
    public void shouldNotHaveNextElement()
    {
        // when
        final CompactListIterator iterator = list.iterator();

        // then
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    public void shouldHaveNextElement()
    {
        // given
        addValues();

        // when
        final CompactListIterator iterator = list.iterator();

        // then
        assertThat(iterator.hasNext()).isTrue();
    }

    @Test
    public void shouldReturnPosition()
    {
        // given
        addValues();
        final CompactListIterator iterator = list.iterator();

        // when
        iterator.next();

        // then
        assertThat(iterator.position()).isEqualTo(0);
    }

    @Test
    public void shouldResetPosition()
    {
        // given
        addValues();
        final CompactListIterator iterator = list.iterator();
        iterator.next();

        // when
        iterator.reset();

        // then
        assertThat(iterator.position()).isEqualTo(-1);
    }

    protected void addValues()
    {
        for (int i = 0; i < 10; i++)
        {
            writeBuffer.putInt(0, i);
            list.add(writeBuffer);
        }
    }
}
