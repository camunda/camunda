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
public class ContextualLogger extends DelegatingLogger {
  private static final String SEPARATOR = " - ";
  private final LoggerContext context;

  public ContextualLogger(final Logger delegate, final LoggerContext context) {
    super(delegate);
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
  public void trace(final String msg) {
    if (isTraceEnabled()) {
      super.trace(contextualize(msg));
    }
  }

  @Override
  public void trace(final String format, final Object arg) {
    if (isTraceEnabled()) {
      super.trace(contextualize(format), arg);
    }
  }

  @Override
  public void trace(final String format, final Object arg1, final Object arg2) {
    if (isTraceEnabled()) {
      super.trace(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void trace(final String format, final Object... arguments) {
    if (isTraceEnabled()) {
      super.trace(contextualize(format), arguments);
    }
  }

  @Override
  public void trace(final String msg, final Throwable t) {
    if (isTraceEnabled()) {
      super.trace(contextualize(msg), t);
    }
  }

  @Override
  public void trace(final Marker marker, final String msg) {
    if (isTraceEnabled()) {
      super.trace(marker, contextualize(msg));
    }
  }

  @Override
  public void trace(final Marker marker, final String format, final Object arg) {
    if (isTraceEnabled()) {
      super.trace(marker, contextualize(format), arg);
    }
  }

  @Override
  public void trace(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isTraceEnabled()) {
      super.trace(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void trace(final Marker marker, final String format, final Object... argArray) {
    if (isTraceEnabled()) {
      super.trace(marker, contextualize(format), argArray);
    }
  }

  @Override
  public void trace(final Marker marker, final String msg, final Throwable t) {
    if (isTraceEnabled()) {
      super.trace(marker, contextualize(msg), t);
    }
  }

  @Override
  public void debug(final String msg) {
    if (isDebugEnabled()) {
      super.debug(contextualize(msg));
    }
  }

  @Override
  public void debug(final String format, final Object arg) {
    if (isDebugEnabled()) {
      super.debug(contextualize(format), arg);
    }
  }

  @Override
  public void debug(final String format, final Object arg1, final Object arg2) {
    if (isDebugEnabled()) {
      super.debug(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void debug(final String format, final Object... arguments) {
    if (isDebugEnabled()) {
      super.debug(contextualize(format), arguments);
    }
  }

  @Override
  public void debug(final String msg, final Throwable t) {
    if (isDebugEnabled()) {
      super.debug(contextualize(msg), t);
    }
  }

  @Override
  public void debug(final Marker marker, final String msg) {
    if (isDebugEnabled()) {
      super.debug(marker, contextualize(msg));
    }
  }

  @Override
  public void debug(final Marker marker, final String format, final Object arg) {
    if (isDebugEnabled()) {
      super.debug(marker, contextualize(format), arg);
    }
  }

  @Override
  public void debug(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isDebugEnabled()) {
      super.debug(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void debug(final Marker marker, final String format, final Object... arguments) {
    if (isDebugEnabled()) {
      super.debug(marker, contextualize(format), arguments);
    }
  }

  @Override
  public void debug(final Marker marker, final String msg, final Throwable t) {
    if (isDebugEnabled()) {
      super.debug(marker, contextualize(msg), t);
    }
  }

  @Override
  public void info(final String msg) {
    if (isInfoEnabled()) {
      super.info(contextualize(msg));
    }
  }

  @Override
  public void info(final String format, final Object arg) {
    if (isInfoEnabled()) {
      super.info(contextualize(format), arg);
    }
  }

  @Override
  public void info(final String format, final Object arg1, final Object arg2) {
    if (isInfoEnabled()) {
      super.info(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void info(final String format, final Object... arguments) {
    if (isInfoEnabled()) {
      super.info(contextualize(format), arguments);
    }
  }

  @Override
  public void info(final String msg, final Throwable t) {
    if (isInfoEnabled()) {
      super.info(contextualize(msg), t);
    }
  }

  @Override
  public void info(final Marker marker, final String msg) {
    if (isInfoEnabled()) {
      super.info(marker, contextualize(msg));
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object arg) {
    if (isInfoEnabled()) {
      super.info(marker, contextualize(format), arg);
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isInfoEnabled()) {
      super.info(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object... arguments) {
    if (isInfoEnabled()) {
      super.info(marker, contextualize(format), arguments);
    }
  }

  @Override
  public void info(final Marker marker, final String msg, final Throwable t) {
    if (isInfoEnabled()) {
      super.info(marker, contextualize(msg), t);
    }
  }

  @Override
  public void warn(final String msg) {
    if (isWarnEnabled()) {
      super.warn(contextualize(msg));
    }
  }

  @Override
  public void warn(final String format, final Object arg) {
    if (isWarnEnabled()) {
      super.warn(contextualize(format), arg);
    }
  }

  @Override
  public void warn(final String format, final Object... arguments) {
    if (isWarnEnabled()) {
      super.warn(contextualize(format), arguments);
    }
  }

  @Override
  public void warn(final String format, final Object arg1, final Object arg2) {
    if (isWarnEnabled()) {
      super.warn(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void warn(final String msg, final Throwable t) {
    if (isWarnEnabled()) {
      super.warn(contextualize(msg), t);
    }
  }

  @Override
  public void warn(final Marker marker, final String msg) {
    if (isWarnEnabled()) {
      super.warn(marker, contextualize(msg));
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg) {
    if (isWarnEnabled()) {
      super.warn(marker, contextualize(format), arg);
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isWarnEnabled()) {
      super.warn(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object... arguments) {
    if (isWarnEnabled()) {
      super.warn(marker, contextualize(format), arguments);
    }
  }

  @Override
  public void warn(final Marker marker, final String msg, final Throwable t) {
    if (isWarnEnabled()) {
      super.warn(marker, contextualize(msg), t);
    }
  }

  @Override
  public void error(final String msg) {
    if (isErrorEnabled()) {
      super.error(contextualize(msg));
    }
  }

  @Override
  public void error(final String format, final Object arg) {
    if (isErrorEnabled()) {
      super.error(contextualize(format), arg);
    }
  }

  @Override
  public void error(final String format, final Object arg1, final Object arg2) {
    if (isErrorEnabled()) {
      super.error(contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void error(final String format, final Object... arguments) {
    if (isErrorEnabled()) {
      super.error(contextualize(format), arguments);
    }
  }

  @Override
  public void error(final String msg, final Throwable t) {
    if (isErrorEnabled()) {
      super.error(contextualize(msg), t);
    }
  }

  @Override
  public void error(final Marker marker, final String msg) {
    if (isErrorEnabled()) {
      super.error(marker, contextualize(msg));
    }
  }

  @Override
  public void error(final Marker marker, final String format, final Object arg) {
    if (isErrorEnabled()) {
      super.error(marker, contextualize(format), arg);
    }
  }

  @Override
  public void error(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isErrorEnabled()) {
      super.error(marker, contextualize(format), arg1, arg2);
    }
  }

  @Override
  public void error(final Marker marker, final String format, final Object... arguments) {
    if (isErrorEnabled()) {
      super.error(marker, contextualize(format), arguments);
    }
  }

  @Override
  public void error(final Marker marker, final String msg, final Throwable t) {
    if (isErrorEnabled()) {
      super.error(marker, contextualize(msg), t);
    }
  }
}
