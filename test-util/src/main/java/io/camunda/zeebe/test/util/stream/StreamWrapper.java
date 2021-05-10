/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.stream;

import java.util.ArrayList;
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

public abstract class StreamWrapper<T, S extends StreamWrapper<T, S>> implements Stream<T> {
  private final Stream<T> wrappedStream;

  public StreamWrapper(final Stream<T> wrappedStream) {
    this.wrappedStream = wrappedStream;
  }

  protected abstract S supply(Stream<T> wrappedStream);

  /**
   * Skips elements until the predicate is matched. Retains the first element that matches the
   * predicate.
   */
  public S skipUntil(final Predicate<T> predicate) {
    return supply(dropWhile(predicate.negate()));
  }

  /**
   * short-circuiting operation; limits the stream to the first element that fulfills the predicate
   */
  public S limit(final Predicate<T> predicate) {
    // looks a bit hacky but we can't use takeWhile(predicate.negate()) here
    // because this doesn't include the element that fulfills the predicate
    // and the underlying record iterator blocks on hasNext()

    final var records = new ArrayList<T>();
    wrappedStream.peek(records::add).filter(predicate).findFirst();
    return supply(records.stream());
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
  public <R> Stream<R> map(final Function<? super T, ? extends R> mapper) {
    return wrappedStream.map(mapper);
  }

  @Override
  public IntStream mapToInt(final ToIntFunction<? super T> mapper) {
    return wrappedStream.mapToInt(mapper);
  }

  @Override
  public LongStream mapToLong(final ToLongFunction<? super T> mapper) {
    return wrappedStream.mapToLong(mapper);
  }

  @Override
  public DoubleStream mapToDouble(final ToDoubleFunction<? super T> mapper) {
    return wrappedStream.mapToDouble(mapper);
  }

  @Override
  public <R> Stream<R> flatMap(final Function<? super T, ? extends Stream<? extends R>> mapper) {
    return wrappedStream.flatMap(mapper);
  }

  @Override
  public IntStream flatMapToInt(final Function<? super T, ? extends IntStream> mapper) {
    return wrappedStream.flatMapToInt(mapper);
  }

  @Override
  public LongStream flatMapToLong(final Function<? super T, ? extends LongStream> mapper) {
    return wrappedStream.flatMapToLong(mapper);
  }

  @Override
  public DoubleStream flatMapToDouble(final Function<? super T, ? extends DoubleStream> mapper) {
    return wrappedStream.flatMapToDouble(mapper);
  }

  @Override
  public S distinct() {
    return supply(wrappedStream.distinct());
  }

  @Override
  public S sorted() {
    return supply(wrappedStream.sorted());
  }

  // Generate delegate methods

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
  public void forEach(final Consumer<? super T> action) {
    wrappedStream.forEach(action);
  }

  @Override
  public void forEachOrdered(final Consumer<? super T> action) {
    wrappedStream.forEachOrdered(action);
  }

  @Override
  public Object[] toArray() {
    return wrappedStream.toArray();
  }

  @Override
  public <A> A[] toArray(final IntFunction<A[]> generator) {
    return wrappedStream.toArray(generator);
  }

  @Override
  public T reduce(final T identity, final BinaryOperator<T> accumulator) {
    return wrappedStream.reduce(identity, accumulator);
  }

  @Override
  public Optional<T> reduce(final BinaryOperator<T> accumulator) {
    return wrappedStream.reduce(accumulator);
  }

  @Override
  public <U> U reduce(
      final U identity,
      final BiFunction<U, ? super T, U> accumulator,
      final BinaryOperator<U> combiner) {
    return wrappedStream.reduce(identity, accumulator, combiner);
  }

  @Override
  public <R> R collect(
      final Supplier<R> supplier,
      final BiConsumer<R, ? super T> accumulator,
      final BiConsumer<R, R> combiner) {
    return wrappedStream.collect(supplier, accumulator, combiner);
  }

  @Override
  public <R, A> R collect(final Collector<? super T, A, R> collector) {
    return wrappedStream.collect(collector);
  }

  @Override
  public Optional<T> min(final Comparator<? super T> comparator) {
    return wrappedStream.min(comparator);
  }

  @Override
  public Optional<T> max(final Comparator<? super T> comparator) {
    return wrappedStream.max(comparator);
  }

  @Override
  public long count() {
    return wrappedStream.count();
  }

  @Override
  public boolean anyMatch(final Predicate<? super T> predicate) {
    return wrappedStream.anyMatch(predicate);
  }

  @Override
  public boolean allMatch(final Predicate<? super T> predicate) {
    return wrappedStream.allMatch(predicate);
  }

  @Override
  public boolean noneMatch(final Predicate<? super T> predicate) {
    return wrappedStream.noneMatch(predicate);
  }

  @Override
  public Optional<T> findFirst() {
    return wrappedStream.findFirst();
  }

  @Override
  public Optional<T> findAny() {
    return wrappedStream.findAny();
  }

  @Override
  public Iterator<T> iterator() {
    return wrappedStream.iterator();
  }

  @Override
  public Spliterator<T> spliterator() {
    return wrappedStream.spliterator();
  }

  @Override
  public boolean isParallel() {
    return wrappedStream.isParallel();
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

  @Override
  public void close() {
    wrappedStream.close();
  }
}
