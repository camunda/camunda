/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.aot;

import io.camunda.zeebe.client.impl.response.BrokerInfoImpl;
import io.camunda.zeebe.client.impl.response.PartitionInfoImpl;
import io.camunda.zeebe.client.impl.response.TopologyImpl;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public final class Features implements Feature {
  private final Set<Object> registered = Collections.synchronizedSet(new HashSet<>());

  @Override
  public void beforeAnalysis(final BeforeAnalysisAccess access) {
    registerForReflection();
  }

  /** Registers several classes for Jackson so it can deserialize them */
  private void registerForReflection() {
    registerClass(TopologyImpl.class, BrokerInfoImpl.class, PartitionInfoImpl.class);
  }

  private void registerClass(final Class<?>... classes) {
    Arrays.stream(classes).forEach(this::registerClass);
  }

  private void registerClass(final Class<?> clazz) {
    // register the class so ClassLoader.loadClass can return it
    if (!registered.add(clazz)) {
      return;
    }

    RuntimeReflection.register(clazz);

    // register all fields, methods, etc.
    registerMethod(clazz.getMethods());
    registerExecutable(clazz.getConstructors());
    registerField(clazz.getFields());

    // walk the hierarchy and register all appropriate types
    registerClass(clazz.getInterfaces());
  }

  private void registerMethod(final Method... methods) {
    Arrays.stream(methods).forEach(this::registerMethod);
  }

  private void registerMethod(final Method method) {
    registerExecutable(method);
    RuntimeReflection.register(method.getReturnType());
  }

  private void registerExecutable(final Executable... methods) {
    Arrays.stream(methods).forEach(this::registerExecutable);
  }

  private void registerExecutable(final Executable method) {
    if (!registered.add(method)) {
      return;
    }

    RuntimeReflection.register(method);
    RuntimeReflection.registerAsQueried(method);
    RuntimeReflection.register(method.getParameterTypes());
  }

  private void registerField(final Field... fields) {
    Arrays.stream(fields).forEach(this::registerField);
  }

  private void registerField(final Field field) {
    if (!registered.add(field)) {
      return;
    }

    RuntimeReflection.register(field);
    RuntimeReflection.register(field.getType());
  }
}
