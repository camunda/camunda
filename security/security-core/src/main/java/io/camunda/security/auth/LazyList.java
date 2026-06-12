/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Package-private {@link List} decorator that defers materialisation of its backing list to a
 * {@link Supplier} invoked at most once on the first read operation. Subsequent operations delegate
 * to the memoised list.
 *
 * <p>Used by {@link CamundaAuthentication} so hosts can hand the builder a deferred resolver for a
 * membership field (groups, roles, tenants, mapping rules) without changing the public accessor
 * signature, which keeps returning {@code List<String>}.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>Read operations trigger the supplier on first call; the result is memoised.
 *   <li>Mutator operations throw {@link UnsupportedOperationException}, matching the immutability
 *       contract of the lists produced by the canonical constructor today.
 *   <li>{@link #equals}, {@link #hashCode}, {@link #iterator}, {@link #size}, etc. all materialise.
 *   <li>Serialisation goes through {@link #writeReplace}: the wire form is always a plain {@link
 *       List}, so lazy state never crosses a serialisation boundary.
 * </ul>
 */
final class LazyList<T> extends AbstractList<T> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private transient volatile Supplier<List<T>> supplier;
  private transient volatile List<T> materialised;

  LazyList(final Supplier<List<T>> supplier) {
    this.supplier = Objects.requireNonNull(supplier, "supplier must not be null");
  }

  private List<T> resolve() {
    List<T> local = materialised;
    if (local == null) {
      synchronized (this) {
        local = materialised;
        if (local == null) {
          final var produced = supplier.get();
          local = produced == null ? List.of() : List.copyOf(produced);
          materialised = local;
          supplier = null;
        }
      }
    }
    return local;
  }

  boolean hasSupplierReference() {
    return supplier != null;
  }

  @Override
  public T get(final int index) {
    return resolve().get(index);
  }

  @Override
  public int size() {
    return resolve().size();
  }

  @Override
  public boolean isEmpty() {
    return resolve().isEmpty();
  }

  @Override
  public boolean contains(final Object o) {
    return resolve().contains(o);
  }

  @Override
  public Iterator<T> iterator() {
    return resolve().iterator();
  }

  @Override
  public ListIterator<T> listIterator() {
    return resolve().listIterator();
  }

  @Override
  public ListIterator<T> listIterator(final int index) {
    return resolve().listIterator(index);
  }

  @Override
  public Object[] toArray() {
    return resolve().toArray();
  }

  @Override
  public <U> U[] toArray(final U[] a) {
    return resolve().toArray(a);
  }

  @Override
  public boolean containsAll(final Collection<?> c) {
    return resolve().containsAll(c);
  }

  @Override
  public int indexOf(final Object o) {
    return resolve().indexOf(o);
  }

  @Override
  public int lastIndexOf(final Object o) {
    return resolve().lastIndexOf(o);
  }

  @Override
  public List<T> subList(final int fromIndex, final int toIndex) {
    return resolve().subList(fromIndex, toIndex);
  }

  @Override
  public boolean equals(final Object o) {
    return resolve().equals(o);
  }

  @Override
  public int hashCode() {
    return resolve().hashCode();
  }

  @Override
  public String toString() {
    return resolve().toString();
  }

  @Serial
  private Object writeReplace() throws ObjectStreamException {
    return List.copyOf(resolve());
  }
}
