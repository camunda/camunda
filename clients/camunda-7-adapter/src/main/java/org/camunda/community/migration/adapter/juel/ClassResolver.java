/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.juel;

import org.camunda.bpm.engine.ArtifactFactory;
import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * This wraps the access to {@link ClassLoader} and {@link ArtifactFactory} for loading of {@link
 * org.camunda.bpm.engine.delegate.ExecutionListener} and {@link
 * org.camunda.bpm.engine.delegate.JavaDelegate} by FQN String.
 */
@Component
public class ClassResolver {

  private final ArtifactFactory artifactFactory;

  public ClassResolver(ArtifactFactory artifactFactory) {
    this.artifactFactory = artifactFactory;
  }

  public JavaDelegate loadJavaDelegate(String delegateName) {
    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      final Class<? extends JavaDelegate> clazz =
          (Class<? extends JavaDelegate>) contextClassLoader.loadClass(delegateName);
      return artifactFactory.getArtifact(clazz);
    } catch (Exception e) {
      throw new RuntimeException(
          "Could not load delegation class '" + delegateName + "': " + e.getMessage(), e);
    }
  }

  public ExecutionListener loadExecutionListener(String listenerName) {
    final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      final Class<? extends ExecutionListener> clazz =
          (Class<? extends ExecutionListener>) contextClassLoader.loadClass(listenerName);
      return artifactFactory.getArtifact(clazz);
    } catch (Exception e) {
      throw new RuntimeException(
          "Could not load listener class '" + listenerName + "': " + e.getMessage(), e);
    }
  }
}
