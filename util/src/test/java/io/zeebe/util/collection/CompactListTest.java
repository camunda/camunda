/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.collection;

import static io.zeebe.util.collection.CompactListDescriptor.elementDataOffset;
import static io.zeebe.util.collection.CompactListDescriptor.elementOffset;
import static io.zeebe.util.collection.CompactListDescriptor.framedLength;
import static io.zeebe.util.collection.CompactListDescriptor.requiredBufferCapacity;
import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocators;
import io.zeebe.util.allocation.DirectBufferAllocator;
import java.util.Comparator;
import java.util.NoSuchElementException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CompactListTest {
  @Rule public ExpectedException expectedExceptionRule = ExpectedException.none();
  CompactList list;
  UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);
  UnsafeBuffer readBuffer = new UnsafeBuffer(0, 0);

  @Before
  public void setup() {
    list = new CompactList(SIZE_OF_INT, 16, new DirectBufferAllocator());
    writeBuffer.wrap(new byte[SIZE_OF_INT]);
    readBuffer.wrap(new byte[SIZE_OF_INT]);
  }

  @Test
  public void shouldExceptionNotEnoughCapacity() throws Exception {
    // given
    final AllocatedBuffer allocatedBuffer = BufferAllocators.allocateDirect(0);

    // then
    expectedExceptionRule.expect(IllegalArgumentException.class);

    // when
    try (CompactList list = new CompactList(allocatedBuffer, 16, 10)) {}
  }

  @Test
  public void shouldExceptionForElementLengthTooLargeWhenAddingElement() {
    // given
    final UnsafeBuffer element = new UnsafeBuffer(new byte[SIZE_OF_LONG]);

    // then
    expectedExceptionRule.expect(IllegalArgumentException.class);

    // when
    list.add(element);
  }

  @Test
  public void shouldExceptionWhenIndexIsNegativeWhenAddingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.add(writeBuffer, 0, SIZE_OF_INT, -1);
  }

  @Test
  public void shouldExceptionWhenIndexIsGreaterThanSizeWhenAddingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.add(writeBuffer, 0, SIZE_OF_INT, 1);
  }

  @Test
  public void shouldAddElement() {
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
  public void shouldAddElementAtIndex() {
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

  @Test
  public void shouldExceptionForElementLengthTooLargeWhenSettingElement() {
    // given
    final UnsafeBuffer element = new UnsafeBuffer(new byte[SIZE_OF_LONG]);

    // then
    expectedExceptionRule.expect(IllegalArgumentException.class);

    // when
    list.set(0, element);
  }

  @Test
  public void shouldExceptionWhenIndexIsNegativeWhenSettingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.set(-1, writeBuffer);
  }

  @Test
  public void shouldExceptionWhenIndexIsGreaterThanSizeWhenSettingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.set(0, writeBuffer);
  }

  @Test
  public void shouldSetElement() {
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
  public void shouldSetElementAtIndex() {
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

  @Test
  public void shouldExceptionWhenIndexIsNegativeWhenRemovingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.remove(-1);
  }

  @Test
  public void shouldExceptionWhenIndexIsGreaterThanSizeWhenRemovingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.remove(0);
  }

  @Test
  public void shouldRemoveElement() {
    // given
    writeBuffer.putInt(0, 7);
    list.add(writeBuffer);

    // when
    list.remove(0);

    // then
    assertThat(list.size()).isEqualTo(0);
  }

  @Test
  public void shouldRemoveElementAtIndex() {
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
  public void shouldClearList() {
    // given
    addValues();

    // assume
    assertThat(list.size()).isEqualTo(10);

    // when
    list.clear();

    // then
    assertThat(list.size()).isEqualTo(0);
  }

  @Test
  public void shouldClearFullList() {
    // given
    fillList();

    // assume
    assertThat(list.size()).isEqualTo(list.capacity());

    // when
    list.clear();

    // then
    assertThat(list.size()).isEqualTo(0);
  }

  @Test
  public void shouldExceptionWhenIndexIsNegativeWhenGettingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.get(-1, readBuffer, 0);
  }

  @Test
  public void shouldExceptionWhenIndexIsGreaterThanSizeWhenGettingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.get(0, readBuffer, 0);
  }

  @Test
  public void shouldGetElement() {
    // given
    addValues();

    // when
    list.get(7, readBuffer, 0);

    // then
    assertThat(readBuffer.getInt(0)).isEqualTo(7);
  }

  @Test
  public void shouldNotModifyElementInList() {
    // given
    final UnsafeBuffer element = new UnsafeBuffer(new byte[SIZE_OF_INT]);

    addValues();

    list.get(7, element, 0);

    // assume
    assertThat(element.getInt(0)).isEqualTo(7);

    // when
    element.putInt(0, 555);

    // then
    list.get(7, readBuffer, 0);
    assertThat(readBuffer.getInt(0)).isEqualTo(7);
  }

  @Test
  public void shouldReturnLengthOfSuppliedElement() {
    // given
    addValues();

    // when
    final int length = list.get(7, readBuffer, 0);

    // then
    assertThat(length).isEqualTo(SIZE_OF_INT);
  }

  @Test
  public void shouldExceptionWhenIndexIsNegativeWhenWrappingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.wrap(-1, readBuffer);
  }

  @Test
  public void shouldExceptionIndexOutOfBoundWhenWrappingElement() {
    // then
    expectedExceptionRule.expect(IndexOutOfBoundsException.class);

    // when
    list.wrap(0, readBuffer);
  }

  @Test
  public void shouldWrapElement() {
    // given
    addValues();

    // when
    list.wrap(7, readBuffer);

    // then
    assertThat(readBuffer.getInt(0)).isEqualTo(7);
  }

  @Test
  public void shouldChangeValueInList() {
    // given
    final UnsafeBuffer element = new UnsafeBuffer(new byte[SIZE_OF_INT]);

    addValues();

    list.wrap(7, element);

    // assume
    assertThat(element.getInt(0)).isEqualTo(7);

    // when
    element.putInt(0, 555);

    // then
    list.wrap(7, readBuffer);
    assertThat(readBuffer.getInt(0)).isEqualTo(555);
  }

  @Test
  public void shouldReturnLengthOfAttachedElement() {
    // given
    addValues();

    // when
    final int length = list.wrap(7, readBuffer);

    // then
    assertThat(length).isEqualTo(SIZE_OF_INT);
  }

  @Test
  public void shouldFindElement() {
    // given
    addValues();

    final UnsafeBuffer keyBuffer = new UnsafeBuffer(new byte[SIZE_OF_INT]);
    keyBuffer.putInt(0, 7);

    // when
    final int idx =
        list.find(
            keyBuffer,
            new Comparator<DirectBuffer>() {
              @Override
              public int compare(DirectBuffer o1, DirectBuffer o2) {
                return Integer.compare(o1.getInt(0), o2.getInt(0));
              }
            });

    // then
    assertThat(idx).isEqualTo(7);
  }

  @Test
  public void shouldReturnIndexToAdd() {
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
    final int idx =
        list.find(
            keyBuffer,
            new Comparator<DirectBuffer>() {
              @Override
              public int compare(DirectBuffer o1, DirectBuffer o2) {
                return Integer.compare(o1.getInt(0), o2.getInt(0));
              }
            });

    // then
    assertThat(idx).isEqualTo(-5);
    assertThat(~idx).isEqualTo(4);
  }

  @Test
  public void shouldReturnSize() {
    // given
    addValues();

    // when
    final int size = list.size();

    // then
    assertThat(size).isEqualTo(10);
  }

  @Test
  public void shouldReturnMaxElementLength() {
    // given

    // when
    final int maxElementDataLength = list.maxElementDataLength();

    // then
    assertThat(maxElementDataLength).isEqualTo(SIZE_OF_INT);
  }

  @Test
  public void shouldCopyInto() {
    // given
    addValues();

    final int framedElementLength = framedLength(SIZE_OF_INT);
    final UnsafeBuffer copy =
        new UnsafeBuffer(new byte[requiredBufferCapacity(framedElementLength, 16)]);

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
  public void shouldReturnLengthOfCopy() {
    // given
    addValues();

    final int framedElementLength = framedLength(SIZE_OF_INT);
    final UnsafeBuffer copy =
        new UnsafeBuffer(new byte[requiredBufferCapacity(framedElementLength, 16)]);

    // when
    final int length = list.copyInto(copy, 0);

    // then
    assertThat(length).isEqualTo(140);
  }

  @Test
  public void shouldIterate() {
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

  @Test
  public void shouldReturnCapacity() {
    assertThat(list.capacity()).isEqualTo(16);
  }

  @Test
  public void shouldExceptionNoSuchElement() {
    // given
    final CompactListIterator iterator = list.iterator();

    // then
    expectedExceptionRule.expect(NoSuchElementException.class);

    // when
    iterator.next();
  }

  @Test
  public void shouldNotHaveNextElement() {
    // when
    final CompactListIterator iterator = list.iterator();

    // then
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldHaveNextElement() {
    // given
    addValues();

    // when
    final CompactListIterator iterator = list.iterator();

    // then
    assertThat(iterator.hasNext()).isTrue();
  }

  @Test
  public void shouldReturnPosition() {
    // given
    addValues();
    final CompactListIterator iterator = list.iterator();

    // when
    iterator.next();

    // then
    assertThat(iterator.position()).isEqualTo(0);
  }

  @Test
  public void shouldResetPosition() {
    // given
    addValues();
    final CompactListIterator iterator = list.iterator();
    iterator.next();

    // when
    iterator.reset();

    // then
    assertThat(iterator.position()).isEqualTo(-1);
  }

  protected void addValues() {
    for (int i = 0; i < 10; i++) {
      writeBuffer.putInt(0, i);
      list.add(writeBuffer);
    }
  }

  protected void fillList() {
    for (int i = list.size(); i < list.capacity(); i++) {
      writeBuffer.putInt(0, i);
      list.add(writeBuffer);
    }
  }
}
