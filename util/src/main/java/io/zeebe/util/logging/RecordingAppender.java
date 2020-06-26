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
import org.apache.logging.log4j.core.appender.NullAppender;

/**
 * An {@link Appender} decorator which delegates all method to the underlying appender while
 * recording all events it receives through {@link #append(LogEvent)}. These are accessible
 * afterwards through {@link #getAppendedEvents()}, in the order in which they were appended. The
 * default underlying appender is a {@link NullAppender}.
 *
 * <p>Note, that the RecordingAppender when used to record the log events of a {@link
 * io.zeebe.util.ZbLogger}, that the appender can only record logs starting at the enabled log
 * level.
 */
// todo: move this class to zeebe-test-utils
public final class RecordingAppender implements Appender {
  private final Appender delegate;
  private final List<LogEvent> appendedEvents;

  /**
   * Construct a RecordingAppender.
   *
   * @param delegate The underlying appender to delegate all log events to
   */
  public RecordingAppender(final Appender delegate) {
    this.delegate = delegate;
    this.appendedEvents = new ArrayList<>();
  }

  /** Construct a RecordingAppender using a NullAppender as underlying appender. */
  public RecordingAppender() {
    this(NullAppender.createAppender("RecordingAppender"));
  }

  @Override
  public void append(final LogEvent event) {
    appendedEvents.add(event.toImmutable());
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
