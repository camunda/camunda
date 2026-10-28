/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import io.camunda.application.commons.pt.LazyPhysicalTenantResource;
import io.camunda.db.rdbms.write.RdbmsMapperBundle.MapperSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Hands out a physical tenant's mappers without building its MyBatis: each mapper builds it on its
 * first call, so the readers and writers that hold the mappers can be created on every node at no
 * cost for a tenant the node never uses.
 */
@NullMarked
final class LazyMappers implements MapperSource {

  private final LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatis;

  LazyMappers(final LazyPhysicalTenantResource<PhysicalTenantMyBatis> myBatis) {
    this.myBatis = myBatis;
  }

  @Override
  public <T> T getMapper(final Class<T> type) {
    return type.cast(
        Proxy.newProxyInstance(
            type.getClassLoader(), new Class<?>[] {type}, new LazyMapper<>(type)));
  }

  private final class LazyMapper<T> implements InvocationHandler {

    private final Class<T> type;
    private volatile @Nullable T target;

    private LazyMapper(final Class<T> type) {
      this.type = type;
    }

    @Override
    public @Nullable Object invoke(
        final Object proxy, final Method method, final @Nullable Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "equals" -> args != null && proxy == args[0];
          case "hashCode" -> System.identityHashCode(proxy);
          case "toString" ->
              "%s of physical tenant '%s'".formatted(type.getName(), myBatis.physicalTenantId());
          default -> throw new UnsupportedOperationException(method.getName());
        };
      }
      try {
        return method.invoke(target(), args);
      } catch (final InvocationTargetException e) {
        throw e.getCause();
      }
    }

    private T target() {
      var resolved = target;
      if (resolved == null) {
        resolved = myBatis.get().sqlSessionTemplate().getMapper(type);
        target = resolved;
      }
      return resolved;
    }
  }
}
