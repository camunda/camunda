/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ArrayValue;
import io.camunda.zeebe.msgpack.value.BaseValue;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public final class ArrayValueTest {

  private final MsgPackWriter writer = new MsgPackWriter();
  private final MsgPackReader reader = new MsgPackReader();
  private final ArrayValue<IntegerValue> array = new ArrayValue<>(IntegerValue::new);

  @Test
  void shouldAppendValues() {
    // when
    addIntValues(array, 1, 2, 3);

    // then
    encodeAndDecode(array);
    assertIntValues(array, 1, 2, 3);
  }

  @Test
  void shouldAddValueAtBeginning() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    array.add(0).setValue(4);
    array.add(1).setValue(5);
    array.add(2).setValue(6);

    // then
    encodeAndDecode(array);
    assertIntValues(array, 4, 5, 6, 1, 2, 3);
  }

  @Test
  void shouldAddValueInBetween() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    array.add(1).setValue(4);
    array.add(2).setValue(5);
    array.add(3).setValue(6);

    // then
    encodeAndDecode(array);
    assertIntValues(array, 1, 4, 5, 6, 2, 3);
  }

  @Test
  void shouldAddValuesAtEnd() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    addIntValues(array, 4, 5, 6);

    // then
    encodeAndDecode(array);
    assertIntValues(array, 1, 2, 3, 4, 5, 6);
  }

  @Test
  void shouldUpdateValues() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    final Iterator<IntegerValue> iterator = array.iterator();
    iterator.next().setValue(4);
    iterator.next().setValue(5);
    iterator.next().setValue(6);

    // then
    encodeAndDecode(array);
    assertIntValues(array, 4, 5, 6);
  }

  @Test
  void shouldSerializeValuesAfterPartialRead() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    final Iterator<IntegerValue> iterator = array.iterator();
    iterator.next();
    iterator.next();

    // then
    encodeAndDecode(array);
    assertIntValues(array, 1, 2, 3);
  }

  @Test
  void shouldRemoveValueAtBeginning() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    final Iterator<IntegerValue> iterator = array.iterator();
    iterator.next();
    iterator.remove();

    // then
    encodeAndDecode(array);
    assertIntValues(array, 2, 3);
  }

  @Test
  void shouldRemoveValueInBetween() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    final Iterator<IntegerValue> iterator = array.iterator();
    iterator.next();
    iterator.next();
    iterator.remove();

    // then
    encodeAndDecode(array);
    assertIntValues(array, 1, 3);
  }

  @Test
  void shouldRemoveValueAtEnd() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    final Iterator<IntegerValue> iterator = array.iterator();
    iterator.next();
    iterator.next();
    iterator.next();
    iterator.remove();

    // then
    encodeAndDecode(array);
    assertIntValues(array, 1, 2);
  }

  @Test
  void shouldRemoveAllValues() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    final Iterator<IntegerValue> iterator = array.iterator();
    iterator.next();
    iterator.remove();
    iterator.next();
    iterator.remove();
    iterator.next();
    iterator.remove();

    // then
    encodeAndDecode(array);
    assertIntValues(array);
  }

  @Test
  void shouldNotInvalidElementOnRemove() {
    // given
    addIntValues(array, 1, 2, 3);

    // when
    final Iterator<IntegerValue> iterator = array.iterator();
    final IntegerValue element = iterator.next();
    iterator.remove();

    // then
    assertThat(element.getValue()).isOne();
    encodeAndDecode(array);
    assertIntValues(array, 2, 3);
  }

  @Test
  void shouldUpdateWithSmallerValue() {
    // given
    final ArrayValue<StringValue> array = new ArrayValue<>(StringValue::new);
    addStringValues(array, "foo", "bar", "baz");

    // when
    final Iterator<StringValue> iterator = array.iterator();
    StringValue element = iterator.next();
    element.wrap(BufferUtil.wrapString("a"));
    element = iterator.next();
    element.wrap(BufferUtil.wrapString("b"));
    element = iterator.next();
    element.wrap(BufferUtil.wrapString("c"));

    // then
    encodeAndDecode(array);
    assertStringValues(array, "a", "b", "c");
  }

  @Test
  void shouldUpdateWithBiggerValue() {
    // given
    final ArrayValue<StringValue> array = new ArrayValue<>(StringValue::new);
    addStringValues(array, "foo", "bar", "baz");

    // when
    final Iterator<StringValue> iterator = array.iterator();
    StringValue element = iterator.next();
    element.wrap(BufferUtil.wrapString("hello"));
    element = iterator.next();
    element.wrap(BufferUtil.wrapString("world"));
    element = iterator.next();
    element.wrap(BufferUtil.wrapString("friend"));

    // then
    encodeAndDecode(array);
    assertStringValues(array, "hello", "world", "friend");
  }

  @Test
  void shouldIncreaseInternalBufferWhenAddingToEnd() {
    // given
    final int valueCount = 10_000;

    final Integer[] values =
        IntStream.iterate(0, (i) -> ++i).limit(valueCount).boxed().toArray(Integer[]::new);

    // when
    addIntValues(array, values);

    // then
    encodeAndDecode(array);
    assertIntValues(array, values);
  }

  @Test
  void shouldIncreaseInternalBufferWhenAddingToBeginning() {
    // given
    final int valueCount = 10_000;
    final List<Integer> generatedList =
        IntStream.iterate(0, (i) -> ++i).limit(valueCount).boxed().collect(Collectors.toList());
    final List<Integer> reverseList = new ArrayList<>(generatedList);
    Collections.reverse(generatedList);

    final Integer[] values = generatedList.toArray(new Integer[valueCount]);

    // when
    array.add().setValue(values[0]);
    for (int i = 1; i < values.length; i++) {
      array.add(0).setValue(values[i]);
    }

    // then
    encodeAndDecode(array);

    final Integer[] resultValues = reverseList.toArray(new Integer[valueCount]);
    assertIntValues(array, resultValues);
  }

  @Test
  void shouldSerializeUndeclaredProperties() {
    // given
    final ArrayValue<Foo> fooArray = new ArrayValue<>(Foo::new);
    fooArray.add().setFoo("foo").setBar("bar");

    final DirectBuffer buffer = encode(fooArray);

    final ArrayValue<Bar> barArray = new ArrayValue<>(Bar::new);

    // when
    decode(barArray, buffer);
    barArray.iterator().next().setBar("barbar");

    // then
    encodeAndDecode(barArray);

    final Bar element = barArray.iterator().next();
    assertThat(element.getBar()).isEqualTo("barbar");
  }

  @Test
  void shouldThrowExceptionIfNoNextElementExists() {
    // when - then
    assertThatThrownBy(() -> array.iterator().next()).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void shouldThrowExceptionIfRemoveWithoutNext() {
    // when - then
    assertThatThrownBy(() -> array.iterator().remove()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldThrowExceptionIfRemoveIsCalledTwice() {
    // given
    array.add().setValue(1);
    final Iterator<IntegerValue> iterator = array.iterator();

    iterator.next();
    iterator.remove();

    // when - then
    assertThatThrownBy(iterator::remove).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldWriteJson() {
    // given
    final ArrayValue<MinimalPOJO> array = new ArrayValue<>(MinimalPOJO::new);
    array.add().setLongProp(1);
    array.add().setLongProp(2);
    array.add().setLongProp(3);

    final StringBuilder builder = new StringBuilder();

    // when
    array.writeJSON(builder);

    // then
    assertThat(builder).hasToString("[{\"longProp\":1},{\"longProp\":2},{\"longProp\":3}]");
  }

  @Test
  void shouldReturnTrueIfIsEmptyIsCalledWithoutElements() {
    // given no elements are added
    // when
    final boolean isEmpty = array.isEmpty();

    // then
    assertThat(isEmpty).isTrue();
  }

  @Test
  void shouldReturnFalseIfIsEmptyIsCalledWithElements() {
    // given
    addIntValues(array, 1);

    // when
    final boolean isEmpty = array.isEmpty();

    // then
    assertThat(isEmpty).isFalse();
  }

  @Test
  void shouldBeEqual() {
    // given
    final var other = new ArrayValue<>(IntegerValue::new);
    addIntValues(array, 1, 2, 3);
    array.iterator(); // force flush

    // when
    other.add().setValue(1);
    other.add().setValue(2);
    other.add().setValue(3);

    // then - fails because it's not flushed
    assertThat((Object) other).isEqualTo(array);
  }

  @Test
  void shouldHashLatestModification() {
    // given
    final var other = new ArrayValue<>(IntegerValue::new);
    addIntValues(array, 1, 2, 3);
    array.iterator(); // force flush

    // when
    other.add().setValue(1);
    other.add().setValue(2);
    other.add().setValue(3);

    // then - fails because it's not flushed
    assertThat((Object) other).hasSameHashCodeAs(array);
  }

  @Test
  void shouldRemoveSetOfItems() {
    // given
    final var array = new ArrayValue<>(IntegerValue::new);
    addIntValues(array, 1, 2, 3, 4, 5);

    // when
    array.remove(Set.of(new IntegerValue(2), new IntegerValue(4)));

    // then
    assertIntValues(array, 1, 3, 5);
  }

  @Test
  void shouldRemoveAllItems() {
    // given
    final var array = new ArrayValue<>(IntegerValue::new);
    addIntValues(array, 1, 2, 3);

    // when
    array.remove(Set.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3)));

    // then
    assertThat(array.isEmpty()).isTrue();
  }

  @Test
  void shouldNotRemoveAnyItemsIfSetIsEmpty() {
    // given
    final var array = new ArrayValue<>(IntegerValue::new);
    addIntValues(array, 1, 2, 3);

    // when
    array.remove(Set.of());

    // then
    assertIntValues(array, 1, 2, 3);
  }

  @Test
  void shouldHandleRemovingNonExistentItems() {
    // given
    final var array = new ArrayValue<>(IntegerValue::new);
    addIntValues(array, 1, 2, 3);

    // when
    array.remove(Set.of(new IntegerValue(4), new IntegerValue(5)));

    // then
    assertIntValues(array, 1, 2, 3);
  }

  // Helpers

  private void addIntValues(final ArrayValue<IntegerValue> array, final Integer... values) {
    for (final Integer value : values) {
      array.add().setValue(value);
    }
  }

  private void assertIntValues(final ArrayValue<IntegerValue> array, final Integer... expected) {
    final List<Integer> values =
        StreamSupport.stream(array.spliterator(), false)
            .map(IntegerValue::getValue)
            .collect(Collectors.toList());
    assertThat(values).containsExactly(expected);
  }

  private void addStringValues(final ArrayValue<StringValue> array, final String... values) {
    for (final String value : values) {
      array.add().wrap(BufferUtil.wrapString(value));
    }
  }

  private void assertStringValues(final ArrayValue<StringValue> array, final String... expected) {
    final List<String> values =
        StreamSupport.stream(array.spliterator(), false)
            .map(StringValue::getValue)
            .map(BufferUtil::bufferAsString)
            .collect(Collectors.toList());

    assertThat(values).containsExactly(expected);
  }

  private void encodeAndDecode(final BaseValue value) {
    final DirectBuffer buffer = encode(value);
    decode(value, buffer);
  }

  private DirectBuffer encode(final BaseValue value) {
    final int encodedLength = value.getEncodedLength();
    final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[encodedLength]);

    writer.wrap(buffer, 0);
    value.write(writer);

    return buffer;
  }

  private void decode(final BaseValue value, final DirectBuffer buffer) {
    value.reset();

    reader.wrap(buffer, 0, buffer.capacity());
    value.read(reader);
  }

  private static final class Foo extends UnpackedObject {

    private final StringProperty fooProp = new StringProperty("foo");
    private final StringProperty barProp = new StringProperty("bar");

    private Foo() {
      super(2);
      declareProperty(fooProp).declareProperty(barProp);
    }

    public Foo setFoo(final String foo) {
      fooProp.setValue(foo);
      return this;
    }

    void setBar(final String bar) {
      barProp.setValue(bar);
    }
  }

  private static final class Bar extends UnpackedObject {

    private final StringProperty barProp = new StringProperty("bar");

    private Bar() {
      super(1);
      declareProperty(barProp);
    }

    public String getBar() {
      return BufferUtil.bufferAsString(barProp.getValue());
    }

    void setBar(final String bar) {
      barProp.setValue(bar);
    }
  }
}
