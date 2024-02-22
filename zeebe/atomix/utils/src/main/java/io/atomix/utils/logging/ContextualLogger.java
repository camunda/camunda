/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.logging;

import org.slf4j.Logger;
import org.slf4j.Marker;

/** Contextual logger. */
public class ContextualLogger implements Logger {
  private static final String SEPARATOR = " - ";
  private final LoggerContext context;
  private final Logger delegate;

  public ContextualLogger(final Logger delegate, final LoggerContext context) {
    this.delegate = delegate;
    this.context = context;
  }

  /**
   * Returns a contextualized version of the given string.
   *
   * @param msg the message to contextualize
   * @return the contextualized message
   */
  private String contextualize(final String msg) {
    return context + SEPARATOR + msg;
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public boolean isTraceEnabled() {
    return delegate.isTraceEnabled();
  }

  @Override
  public void trace(final String msg) {
    if (isTraceEnabled()) {
      delegate.trace(contextualize(msg));
    }
  }

  @Override
  public void trace(final String format, final Object arg) {
    if (isTraceEnabled()) {
      delegate.trace(contextualize(format), arg);
    }
  }

  @Override
  public void trace(final String format, final Object arg1, final Object arg2) {
    if (isTraceEnabled()) {
      delegate.trace(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void trace(final String format, final Object... arguments) {
    if (isTraceEnabled()) {
      delegate.trace(contextualize(format), arguments);
    }
  }

  @Override
  public void trace(final String msg, final Throwable t) {
    if (isTraceEnabled()) {
      delegate.trace(contextualize(msg), t);
    }
  }

  @Override
  public boolean isTraceEnabled(final Marker marker) {
    return delegate.isTraceEnabled(marker);
  }

  @Override
  public void trace(final Marker marker, final String msg) {
    if (isTraceEnabled()) {
      delegate.trace(marker, contextualize(msg));
    }
  }

  @Override
  public void trace(final Marker marker, final String format, final Object arg) {
    if (isTraceEnabled()) {
      delegate.trace(marker, contextualize(format), arg);
    }
  }

  @Override
  public void trace(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isTraceEnabled()) {
      delegate.trace(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void trace(final Marker marker, final String format, final Object... argArray) {
    if (isTraceEnabled()) {
      delegate.trace(marker, contextualize(format), argArray);
    }
  }

  @Override
  public void trace(final Marker marker, final String msg, final Throwable t) {
    if (isTraceEnabled()) {
      delegate.trace(marker, contextualize(msg), t);
    }
  }

  @Override
  public boolean isDebugEnabled() {
    return delegate.isDebugEnabled();
  }

  @Override
  public void debug(final String msg) {
    if (isDebugEnabled()) {
      delegate.debug(contextualize(msg));
    }
  }

  @Override
  public void debug(final String format, final Object arg) {
    if (isDebugEnabled()) {
      delegate.debug(contextualize(format), arg);
    }
  }

  @Override
  public void debug(final String format, final Object arg1, final Object arg2) {
    if (isDebugEnabled()) {
      delegate.debug(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void debug(final String format, final Object... arguments) {
    if (isDebugEnabled()) {
      delegate.debug(contextualize(format), arguments);
    }
  }

  @Override
  public void debug(final String msg, final Throwable t) {
    if (isDebugEnabled()) {
      delegate.debug(contextualize(msg), t);
    }
  }

  @Override
  public boolean isDebugEnabled(final Marker marker) {
    return delegate.isDebugEnabled(marker);
  }

  @Override
  public void debug(final Marker marker, final String msg) {
    if (isDebugEnabled()) {
      delegate.debug(marker, contextualize(msg));
    }
  }

  @Override
  public void debug(final Marker marker, final String format, final Object arg) {
    if (isDebugEnabled()) {
      delegate.debug(marker, contextualize(format), arg);
    }
  }

  @Override
  public void debug(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isDebugEnabled()) {
      delegate.debug(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void debug(final Marker marker, final String format, final Object... arguments) {
    if (isDebugEnabled()) {
      delegate.debug(marker, contextualize(format), arguments);
    }
  }

  @Override
  public void debug(final Marker marker, final String msg, final Throwable t) {
    if (isDebugEnabled()) {
      delegate.debug(marker, contextualize(msg), t);
    }
  }

  @Override
  public boolean isInfoEnabled() {
    return delegate.isInfoEnabled();
  }

  @Override
  public void info(final String msg) {
    if (isInfoEnabled()) {
      delegate.info(contextualize(msg));
    }
  }

  @Override
  public void info(final String format, final Object arg) {
    if (isInfoEnabled()) {
      delegate.info(contextualize(format), arg);
    }
  }

  @Override
  public void info(final String format, final Object arg1, final Object arg2) {
    if (isInfoEnabled()) {
      delegate.info(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void info(final String format, final Object... arguments) {
    if (isInfoEnabled()) {
      delegate.info(contextualize(format), arguments);
    }
  }

  @Override
  public void info(final String msg, final Throwable t) {
    if (isInfoEnabled()) {
      delegate.info(contextualize(msg), t);
    }
  }

  @Override
  public boolean isInfoEnabled(final Marker marker) {
    return delegate.isInfoEnabled(marker);
  }

  @Override
  public void info(final Marker marker, final String msg) {
    if (isInfoEnabled()) {
      delegate.info(marker, contextualize(msg));
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object arg) {
    if (isInfoEnabled()) {
      delegate.info(marker, contextualize(format), arg);
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isInfoEnabled()) {
      delegate.info(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object... arguments) {
    if (isInfoEnabled()) {
      delegate.info(marker, contextualize(format), arguments);
    }
  }

  @Override
  public void info(final Marker marker, final String msg, final Throwable t) {
    if (isInfoEnabled()) {
      delegate.info(marker, contextualize(msg), t);
    }
  }

  @Override
  public boolean isWarnEnabled() {
    return delegate.isWarnEnabled();
  }

  @Override
  public void warn(final String msg) {
    if (isWarnEnabled()) {
      delegate.warn(contextualize(msg));
    }
  }

  @Override
  public void warn(final String format, final Object arg) {
    if (isWarnEnabled()) {
      delegate.warn(contextualize(format), arg);
    }
  }

  @Override
  public void warn(final String format, final Object... arguments) {
    if (isWarnEnabled()) {
      delegate.warn(contextualize(format), arguments);
    }
  }

  @Override
  public void warn(final String format, final Object arg1, final Object arg2) {
    if (isWarnEnabled()) {
      delegate.warn(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void warn(final String msg, final Throwable t) {
    if (isWarnEnabled()) {
      delegate.warn(contextualize(msg), t);
    }
  }

  @Override
  public boolean isWarnEnabled(final Marker marker) {
    return delegate.isWarnEnabled(marker);
  }

  @Override
  public void warn(final Marker marker, final String msg) {
    if (isWarnEnabled()) {
      delegate.warn(marker, contextualize(msg));
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg) {
    if (isWarnEnabled()) {
      delegate.warn(marker, contextualize(format), arg);
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isWarnEnabled()) {
      delegate.warn(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object... arguments) {
    if (isWarnEnabled()) {
      delegate.warn(marker, contextualize(format), arguments);
    }
  }

  @Override
  public void warn(final Marker marker, final String msg, final Throwable t) {
    if (isWarnEnabled()) {
      delegate.warn(marker, contextualize(msg), t);
    }
  }

  @Override
  public boolean isErrorEnabled() {
    return delegate.isErrorEnabled();
  }

  @Override
  public void error(final String msg) {
    if (isErrorEnabled()) {
      delegate.error(contextualize(msg));
    }
  }

  @Override
  public void error(final String format, final Object arg) {
    if (isErrorEnabled()) {
      delegate.error(contextualize(format), arg);
    }
  }

  @Override
  public void error(final String format, final Object arg1, final Object arg2) {
    if (isErrorEnabled()) {
      delegate.error(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void error(final String format, final Object... arguments) {
    if (isErrorEnabled()) {
      delegate.error(contextualize(format), arguments);
    }
  }

  @Override
  public void error(final String msg, final Throwable t) {
    if (isErrorEnabled()) {
      delegate.error(contextualize(msg), t);
    }
  }

  @Override
  public boolean isErrorEnabled(final Marker marker) {
    return delegate.isWarnEnabled(marker);
  }

  @Override
  public void error(final Marker marker, final String msg) {
    if (isErrorEnabled()) {
      delegate.error(marker, contextualize(msg));
    }
  }

  @Override
  public void error(final Marker marker, final String format, final Object arg) {
    if (isErrorEnabled()) {
      delegate.error(marker, contextualize(format), arg);
    }
  }

  @Override
  public void error(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isErrorEnabled()) {
      delegate.error(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void error(final Marker marker, final String format, final Object... arguments) {
    if (isErrorEnabled()) {
      delegate.error(marker, contextualize(format), arguments);
    }
  }

  @Override
  public void error(final Marker marker, final String msg, final Throwable t) {
    if (isErrorEnabled()) {
      delegate.error(marker, contextualize(msg), t);
    }
  }
}
