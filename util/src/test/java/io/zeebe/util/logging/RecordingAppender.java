/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.logging;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;

/**
 * An {@link Appender} decorator which delegates all method to the underlying appender while
 * recording all events it receives through {@link #append(LogEvent)}. These are accessible
 * afterwards through {@link #getAppendedEvents()}, in the order in which they were appended.
 */
final class RecordingAppender implements Appender {
  private final Appender delegate;
  private final List<LogEvent> appendedEvents;

  public RecordingAppender(final Appender delegate) {
    this.delegate = delegate;
    this.appendedEvents = new ArrayList<>();
  }

  @Override
  public void append(final LogEvent event) {
    appendedEvents.add(event);
    delegate.append(event);
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public Layout<? extends Serializable> getLayout() {
    return delegate.getLayout();
  }

  @Override
  public boolean ignoreExceptions() {
    return delegate.ignoreExceptions();
  }

  @Override
  public ErrorHandler getHandler() {
    return delegate.getHandler();
  }

  @Override
  public void setHandler(final ErrorHandler handler) {
    delegate.setHandler(handler);
  }

  public List<LogEvent> getAppendedEvents() {
    return appendedEvents;
  }

  @Override
  public State getState() {
    return delegate.getState();
  }

  @Override
  public void initialize() {
    delegate.initialize();
  }

  @Override
  public void start() {
    delegate.start();
  }

  @Override
  public void stop() {
    delegate.stop();
  }

  @Override
  public boolean isStarted() {
    return delegate.isStarted();
  }

  @Override
  public boolean isStopped() {
    return delegate.isStopped();
  }
}
