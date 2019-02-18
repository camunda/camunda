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
import java.util.List;
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
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;

public abstract class StreamWrapper<T, S extends StreamWrapper<T, S>> implements Stream<T> {
  private final Stream<T> wrappedStream;

  public StreamWrapper(Stream<T> wrappedStream) {
    this.wrappedStream = wrappedStream;
  }

  protected abstract S supply(Stream<T> wrappedStream);

  /**
   * Skips elements until the predicate is matched. Retains the first element that matches the
   * predicate.
   */
  public S skipUntil(Predicate<T> predicate) {
    return supply(StreamEx.of(this).dropWhile(predicate.negate()));
  }

  /**
   * short-circuiting operation; limits the stream to the first element that fulfills the predicate
   */
  public S limit(Predicate<T> predicate) {
    // #takeWhile comes with Java >= 9
    return supply(StreamEx.of(this).takeWhileInclusive(predicate.negate()));
  }

  // Helper to extract values

  public boolean exists() {
    return wrappedStream.findFirst().isPresent();
  }

  public void await() {
    if (!exists()) {
      throw new StreamWrapperException();
    }
  }

  public T getFirst() {
    return wrappedStream.findFirst().orElseThrow(StreamWrapperException::new);
  }

  public T getLast() {
    final List<T> list = asList();

    if (list.isEmpty()) {
      throw new StreamWrapperException();
    }

    return list.get(list.size() - 1);
  }

  public List<T> asList() {
    return wrappedStream.collect(Collectors.toList());
  }

  // Custom delegate methods to fit generics

  @Override
  public S filter(final Predicate<? super T> predicate) {
    return supply(wrappedStream.filter(predicate));
  }

  @Override
  public S distinct() {
    return supply(wrappedStream.distinct());
  }

  @Override
  public S sorted() {
    return supply(wrappedStream.sorted());
  }

  @Override
  public S sorted(final Comparator<? super T> comparator) {
    return supply(wrappedStream.sorted(comparator));
  }

  @Override
  public S peek(final Consumer<? super T> consumer) {
    return supply(wrappedStream.peek(consumer));
  }

  @Override
  public S limit(final long l) {
    return supply(wrappedStream.limit(l));
  }

  @Override
  public S skip(final long l) {
    return supply(wrappedStream.skip(l));
  }

  @Override
  public S sequential() {
    return supply(wrappedStream.sequential());
  }

  @Override
  public S parallel() {
    return supply(wrappedStream.parallel());
  }

  @Override
  public S unordered() {
    return supply(wrappedStream.unordered());
  }

  @Override
  public S onClose(final Runnable runnable) {
    return supply(wrappedStream.onClose(runnable));
  }

  // Generate delegate methods

  public Iterator<T> iterator() {
    return wrappedStream.iterator();
  }

  public Spliterator<T> spliterator() {
    return wrappedStream.spliterator();
  }

  public boolean isParallel() {
    return wrappedStream.isParallel();
  }

  public void close() {
    wrappedStream.close();
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
