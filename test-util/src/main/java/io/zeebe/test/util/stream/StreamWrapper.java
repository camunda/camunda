/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.util.stream;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;

public class StreamWrapper<T> implements Stream<T> {
  private final Stream<T> wrappedStream;

  public StreamWrapper(Stream<T> wrappedStream) {
    this.wrappedStream = wrappedStream;
  }

  /**
   * Skips elements until the predicate is matched. Retains the first element that matches the
   * predicate.
   */
  public StreamWrapper<T> skipUntil(Predicate<T> predicate) {
    return new StreamWrapper<>(StreamEx.of(this).dropWhile(predicate.negate()));
  }

  /**
   * short-circuiting operation; limits the stream to the first element that fulfills the predicate
   */
  public StreamWrapper<T> limit(Predicate<T> predicate) {
    // #takeWhile comes with Java >= 9
    return new StreamWrapper<>(StreamEx.of(this).takeWhileInclusive(predicate.negate()));
  }

  public Iterator<T> iterator() {
    return wrappedStream.iterator();
  }

  public Spliterator<T> spliterator() {
    return wrappedStream.spliterator();
  }

  public boolean isParallel() {
    return wrappedStream.isParallel();
  }

  public Stream<T> sequential() {
    return wrappedStream.sequential();
  }

  public Stream<T> parallel() {
    return wrappedStream.parallel();
  }

  public Stream<T> unordered() {
    return wrappedStream.unordered();
  }

  public Stream<T> onClose(Runnable closeHandler) {
    return wrappedStream.onClose(closeHandler);
  }

  public void close() {
    wrappedStream.close();
  }

  public Stream<T> filter(Predicate<? super T> predicate) {
    return wrappedStream.filter(predicate);
  }

  public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
    return wrappedStream.map(mapper);
  }

  public IntStream mapToInt(ToIntFunction<? super T> mapper) {
    return wrappedStream.mapToInt(mapper);
  }

  public LongStream mapToLong(ToLongFunction<? super T> mapper) {
    return wrappedStream.mapToLong(mapper);
  }

  public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
    return wrappedStream.mapToDouble(mapper);
  }

  public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
    return wrappedStream.flatMap(mapper);
  }

  public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
    return wrappedStream.flatMapToInt(mapper);
  }

  public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
    return wrappedStream.flatMapToLong(mapper);
  }

  public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
    return wrappedStream.flatMapToDouble(mapper);
  }

  public Stream<T> distinct() {
    return wrappedStream.distinct();
  }

  public Stream<T> sorted() {
    return wrappedStream.sorted();
  }

  public Stream<T> sorted(Comparator<? super T> comparator) {
    return wrappedStream.sorted(comparator);
  }

  public Stream<T> peek(Consumer<? super T> action) {
    return wrappedStream.peek(action);
  }

  public Stream<T> limit(long maxSize) {
    return wrappedStream.limit(maxSize);
  }

  public Stream<T> skip(long n) {
    return wrappedStream.skip(n);
  }

  public void forEach(Consumer<? super T> action) {
    wrappedStream.forEach(action);
  }

  public void forEachOrdered(Consumer<? super T> action) {
    wrappedStream.forEachOrdered(action);
  }

  public Object[] toArray() {
    return wrappedStream.toArray();
  }

  public <A> A[] toArray(IntFunction<A[]> generator) {
    return wrappedStream.toArray(generator);
  }

  public T reduce(T identity, BinaryOperator<T> accumulator) {
    return wrappedStream.reduce(identity, accumulator);
  }

  public Optional<T> reduce(BinaryOperator<T> accumulator) {
    return wrappedStream.reduce(accumulator);
  }

  public <U> U reduce(
      U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
    return wrappedStream.reduce(identity, accumulator, combiner);
  }

  public <R> R collect(
      Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
    return wrappedStream.collect(supplier, accumulator, combiner);
  }

  public <R, A> R collect(Collector<? super T, A, R> collector) {
    return wrappedStream.collect(collector);
  }

  public Optional<T> min(Comparator<? super T> comparator) {
    return wrappedStream.min(comparator);
  }

  public Optional<T> max(Comparator<? super T> comparator) {
    return wrappedStream.max(comparator);
  }

  public long count() {
    return wrappedStream.count();
  }

  public boolean anyMatch(Predicate<? super T> predicate) {
    return wrappedStream.anyMatch(predicate);
  }

  public boolean allMatch(Predicate<? super T> predicate) {
    return wrappedStream.allMatch(predicate);
  }

  public boolean noneMatch(Predicate<? super T> predicate) {
    return wrappedStream.noneMatch(predicate);
  }

  public Optional<T> findFirst() {
    return wrappedStream.findFirst();
  }

  public Optional<T> findAny() {
    return wrappedStream.findAny();
  }
}
