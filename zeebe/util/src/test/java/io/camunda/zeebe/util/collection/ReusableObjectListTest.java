/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import org.junit.Test;

/** */
public final class ReusableObjectListTest {
  @Test
  public void shouldAddNewValue() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());

    // when
    reusableObjectList.add().setValue(1);

    // then
    assertThat(reusableObjectList.size()).isEqualTo(1);
    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    assertThat(iterator.hasNext()).isTrue();

    final MutableInt next = iterator.next();
    assertThat(next.getValue()).isEqualTo(1);
  }

  @Test
  public void shouldAddNewBunchOfValues() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    final int workCount = 10_000;

    // when
    for (int i = 0; i < workCount; i++) {
      reusableObjectList.add().setValue(i);
    }

    // then
    assertThat(reusableObjectList.size()).isEqualTo(workCount);

    int count = 0;
    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    while (iterator.hasNext()) {
      final MutableInt next = iterator.next();
      assertThat(next.getValue()).isEqualTo(count);
      count++;
    }
  }

  @Test
  public void shouldRemoveValueViaIterator() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(1);
    reusableObjectList.add().setValue(2);

    // when
    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    final MutableInt removedValue = iterator.next();
    iterator.remove();

    // then
    assertThat(removedValue.getValue()).isEqualTo(0); // reset is called
    assertThat(iterator.hasNext()).isTrue();

    final MutableInt next = iterator.next();
    assertThat(next.getValue()).isEqualTo(2);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldEmptyListViaPollValue() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(2);

    // when
    final MutableInt removedValue = reusableObjectList.poll();

    // then
    assertThat(reusableObjectList.size()).isEqualTo(0);
    assertThat(removedValue.getValue()).isEqualTo(2);

    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldPollValue() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(1);
    reusableObjectList.add().setValue(2);

    // when
    final MutableInt removedValue = reusableObjectList.poll();

    // then
    assertThat(removedValue.getValue()).isEqualTo(1);
    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    assertThat(iterator.hasNext()).isTrue();

    final MutableInt next = iterator.next();
    assertThat(next.getValue()).isEqualTo(2);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldRemoveValueViaIteratorAndResetIterator() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(1);
    reusableObjectList.add().setValue(2);

    // when
    Iterator<MutableInt> iterator = reusableObjectList.iterator();
    final MutableInt removedValue = iterator.next();
    iterator.remove();
    iterator = reusableObjectList.iterator();

    // then
    assertThat(removedValue.getValue()).isEqualTo(0); // reset is called
    assertThat(iterator.hasNext()).isTrue();

    final MutableInt next = iterator.next();
    assertThat(next.getValue()).isEqualTo(2);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldRemoveValueFromList() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(1);
    reusableObjectList.add().setValue(2);

    // when
    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    final MutableInt removedValue = iterator.next();
    reusableObjectList.remove(removedValue);

    // then
    assertThat(removedValue.getValue()).isEqualTo(0); // reset is called
    assertThat(iterator.hasNext()).isTrue();

    final MutableInt next = iterator.next();
    assertThat(next.getValue()).isEqualTo(2);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldRemoveValueFromListAndResetIterator() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(1);
    reusableObjectList.add().setValue(2);

    // when
    Iterator<MutableInt> iterator = reusableObjectList.iterator();
    final MutableInt removedValue = iterator.next();
    reusableObjectList.remove(removedValue);
    iterator = reusableObjectList.iterator();

    // then
    assertThat(removedValue.getValue()).isEqualTo(0); // reset is called
    assertThat(iterator.hasNext()).isTrue();

    final MutableInt next = iterator.next();
    assertThat(next.getValue()).isEqualTo(2);
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldClearList() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(1);
    reusableObjectList.add().setValue(2);

    // when
    reusableObjectList.clear();

    // then
    assertThat(reusableObjectList.size()).isEqualTo(0);

    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    assertThat(iterator.hasNext()).isFalse();
  }

  @Test
  public void shouldAddAfterClearList() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(1);
    reusableObjectList.clear();

    // when
    reusableObjectList.add().setValue(2);

    // then
    assertThat(reusableObjectList.size()).isEqualTo(1);

    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    assertThat(iterator.hasNext()).isTrue();

    final MutableInt next = iterator.next();
    assertThat(next.getValue()).isEqualTo(2);
  }

  @Test
  public void shouldReuseObjectForNewValue() {
    // given
    final ReusableObjectList<MutableInt> reusableObjectList =
        new ReusableObjectList<>(() -> new MutableInt());
    reusableObjectList.add().setValue(1);
    reusableObjectList.add().setValue(2);

    final Iterator<MutableInt> iterator = reusableObjectList.iterator();
    final MutableInt reusedObject = iterator.next();
    iterator.remove();

    // when
    reusableObjectList.add().setValue(3);

    // then
    assertThat(reusedObject.getValue()).isEqualTo(3);
    assertThat(iterator.hasNext()).isTrue();

    final MutableInt next = iterator.next();
    assertThat(next.getValue()).isEqualTo(2);

    // start at begin
    final Iterator<MutableInt> resetedIterator = reusableObjectList.iterator();

    final MutableInt firstValue = resetedIterator.next();
    assertThat(firstValue == reusedObject).isTrue();
    assertThat(firstValue.getValue()).isEqualTo(3);

    final MutableInt secondValue = resetedIterator.next();
    assertThat(secondValue.getValue()).isEqualTo(2);
    assertThat(resetedIterator.hasNext()).isFalse();
  }

  private final class MutableInt implements Reusable {
    private int value;

    public int getValue() {
      return value;
    }

    public void setValue(final int value) {
      this.value = value;
    }

    public void reset() {
      value = 0;
    }
  }
}
