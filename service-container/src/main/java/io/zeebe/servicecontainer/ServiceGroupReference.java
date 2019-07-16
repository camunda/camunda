/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;

public final class ServiceGroupReference<S> {
  @SuppressWarnings("rawtypes")
  private static final BiConsumer NOOP_CONSUMER =
      (n, v) -> {
        // ignore
      };

  protected BiConsumer<ServiceName<S>, S> addHandler;
  protected BiConsumer<ServiceName<S>, S> removeHandler;

  @SuppressWarnings("unchecked")
  private ServiceGroupReference() {
    this(NOOP_CONSUMER, NOOP_CONSUMER);
  }

  private ServiceGroupReference(
      BiConsumer<ServiceName<S>, S> addHandler, BiConsumer<ServiceName<S>, S> removeHandler) {
    this.addHandler = addHandler;
    this.removeHandler = removeHandler;
  }

  public BiConsumer<ServiceName<S>, S> getAddHandler() {
    return addHandler;
  }

  public BiConsumer<ServiceName<S>, S> getRemoveHandler() {
    return removeHandler;
  }

  public static <S> ServiceGroupReference<S> collection(Collection<S> collection) {
    final BiConsumer<ServiceName<S>, S> addHandler = (name, v) -> collection.add(v);
    final BiConsumer<ServiceName<S>, S> removeHandler = (name, v) -> collection.remove(v);

    return new ServiceGroupReference<>(addHandler, removeHandler);
  }

  public static <S, K> ServiceGroupReference<S> map(Map<ServiceName<S>, S> map) {
    final BiConsumer<ServiceName<S>, S> addHandler = (name, v) -> map.put(name, v);
    final BiConsumer<ServiceName<S>, S> removeHandler = (name, v) -> map.remove(name, v);

    return new ServiceGroupReference<>(addHandler, removeHandler);
  }

  public static <S> ReferenceBuilder<S> create() {
    return new ReferenceBuilder<>();
  }

  public static class ReferenceBuilder<S> {
    protected final ServiceGroupReference<S> referenceCollection = new ServiceGroupReference<>();

    public ReferenceBuilder<S> onRemove(BiConsumer<ServiceName<S>, S> removeConsumer) {
      referenceCollection.removeHandler = removeConsumer;
      return this;
    }

    public ReferenceBuilder<S> onAdd(BiConsumer<ServiceName<S>, S> addConsumer) {
      referenceCollection.addHandler = addConsumer;
      return this;
    }

    public ServiceGroupReference<S> build() {
      return referenceCollection;
    }
  }
}
