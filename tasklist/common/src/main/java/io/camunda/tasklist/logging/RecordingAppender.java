/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.logging;

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
 * <p>Note, that the RecordingAppender when used to record the log events of a logger, that the
 * appender can only record logs starting at the enabled log level.
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
    appendedEvents = new ArrayList<>();
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
