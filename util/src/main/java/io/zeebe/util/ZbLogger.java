/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LocationAwareLogger;

/**
 * Delegating Logger implementation for wrapping a LocationAwareLogger
 *
 * <p>If given a {@link LocationAwareLogger} delegate, it will properly calculate the caller
 * location.
 */
public class ZbLogger implements Logger {
  private static final String FQCN = ZbLogger.class.getName();

  private final Logger logger;
  private final String loggerFqcn;
  private final LocationAwareLogger locationAwareLogger;

  public ZbLogger(final Class<?> clazz) {
    this(LoggerFactory.getLogger(clazz));
  }

  public ZbLogger(final String name) {
    this(LoggerFactory.getLogger(name));
  }

  public ZbLogger(final Logger logger) {
    this(logger, FQCN);
  }

  public ZbLogger(final Logger logger, final String loggerFqcn) {
    this.logger = logger;
    this.loggerFqcn = loggerFqcn;

    if (logger instanceof LocationAwareLogger) {
      locationAwareLogger = (LocationAwareLogger) logger;
    } else {
      locationAwareLogger = null;
    }
  }

  @Override
  public String getName() {
    return logger.getName();
  }

  @Override
  public boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }

  @Override
  public void trace(final String msg) {
    if (isTraceEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.TRACE.toInt(), msg, null);
      } else {
        logger.trace(msg);
      }
    }
  }

  @Override
  public void trace(final String format, final Object arg) {
    if (isTraceEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.TRACE.toInt(), format, null, arg);
      } else {
        logger.trace(format, arg);
      }
    }
  }

  @Override
  public void trace(final String format, final Object arg1, final Object arg2) {
    if (isTraceEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.TRACE.toInt(), format, null, arg1, arg2);
      } else {
        logger.trace(format, arg1, arg2);
      }
    }
  }

  @Override
  public void trace(final String format, final Object... arguments) {
    if (isTraceEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.TRACE.toInt(), format, null, arguments);
      } else {
        logger.trace(format, arguments);
      }
    }
  }

  @Override
  public void trace(final String msg, final Throwable t) {
    if (isTraceEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.TRACE.toInt(), msg, t);
      } else {
        logger.trace(msg, t);
      }
    }
  }

  @Override
  public boolean isTraceEnabled(final Marker marker) {
    return logger.isTraceEnabled(marker);
  }

  @Override
  public void trace(final Marker marker, final String msg) {
    if (isTraceEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.TRACE.toInt(), msg, null);
      } else {
        logger.trace(marker, msg);
      }
    }
  }

  @Override
  public void trace(final Marker marker, final String format, final Object arg) {
    if (isTraceEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.TRACE.toInt(), format, null, arg);
      } else {
        logger.trace(marker, format, arg);
      }
    }
  }

  @Override
  public void trace(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isTraceEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.TRACE.toInt(), format, null, arg1, arg2);
      } else {
        logger.trace(marker, format, arg1, arg2);
      }
    }
  }

  @Override
  public void trace(final Marker marker, final String format, final Object... argArray) {
    if (isTraceEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.TRACE.toInt(), format, null, argArray);
      } else {
        logger.trace(marker, format, argArray);
      }
    }
  }

  @Override
  public void trace(final Marker marker, final String msg, final Throwable t) {
    if (isTraceEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.TRACE.toInt(), msg, t);
      } else {
        logger.trace(marker, msg, t);
      }
    }
  }

  @Override
  public boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  @Override
  public void debug(final String msg) {
    if (isDebugEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.DEBUG.toInt(), msg, null);
      } else {
        logger.debug(msg);
      }
    }
  }

  @Override
  public void debug(final String format, final Object arg) {
    if (isDebugEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.DEBUG.toInt(), format, null, arg);
      } else {
        logger.debug(format, arg);
      }
    }
  }

  @Override
  public void debug(final String format, final Object arg1, final Object arg2) {
    if (isDebugEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.DEBUG.toInt(), format, null, arg1, arg2);
      } else {
        logger.debug(format, arg1, arg2);
      }
    }
  }

  @Override
  public void debug(final String format, final Object... arguments) {
    if (isDebugEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.DEBUG.toInt(), format, null, arguments);
      } else {
        logger.debug(format, arguments);
      }
    }
  }

  @Override
  public void debug(final String msg, final Throwable t) {
    if (isDebugEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.DEBUG.toInt(), msg, t);
      } else {
        logger.debug(msg, t);
      }
    }
  }

  @Override
  public boolean isDebugEnabled(final Marker marker) {
    return logger.isDebugEnabled(marker);
  }

  @Override
  public void debug(final Marker marker, final String msg) {
    if (isDebugEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.DEBUG.toInt(), msg, null);
      } else {
        logger.debug(marker, msg);
      }
    }
  }

  @Override
  public void debug(final Marker marker, final String format, final Object arg) {
    if (isDebugEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.DEBUG.toInt(), format, null, arg);
      } else {
        logger.debug(marker, format, arg);
      }
    }
  }

  @Override
  public void debug(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isDebugEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.DEBUG.toInt(), format, null, arg1, arg2);
      } else {
        logger.debug(marker, format, arg1, arg2);
      }
    }
  }

  @Override
  public void debug(final Marker marker, final String format, final Object... arguments) {
    if (isDebugEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.DEBUG.toInt(), format, null, arguments);
      } else {
        logger.debug(marker, format, arguments);
      }
    }
  }

  @Override
  public void debug(final Marker marker, final String msg, final Throwable t) {
    if (isDebugEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.DEBUG.toInt(), msg, t);
      } else {
        logger.debug(marker, msg, t);
      }
    }
  }

  @Override
  public boolean isInfoEnabled() {
    return logger.isInfoEnabled();
  }

  @Override
  public void info(final String msg) {
    if (isInfoEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.INFO.toInt(), msg, null);
      } else {
        logger.info(msg);
      }
    }
  }

  @Override
  public void info(final String format, final Object arg) {
    if (isInfoEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.INFO.toInt(), format, null, arg);
      } else {
        logger.info(format, arg);
      }
    }
  }

  @Override
  public void info(final String format, final Object arg1, final Object arg2) {
    if (isInfoEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.INFO.toInt(), format, null, arg1, arg2);
      } else {
        logger.info(format, arg1, arg2);
      }
    }
  }

  @Override
  public void info(final String format, final Object... arguments) {
    if (isInfoEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.INFO.toInt(), format, null, arguments);
      } else {
        logger.info(format, arguments);
      }
    }
  }

  @Override
  public void info(final String msg, final Throwable t) {
    if (isInfoEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.INFO.toInt(), msg, t);
      } else {
        logger.info(msg, t);
      }
    }
  }

  @Override
  public boolean isInfoEnabled(final Marker marker) {
    return logger.isInfoEnabled(marker);
  }

  @Override
  public void info(final Marker marker, final String msg) {
    if (isInfoEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.INFO.toInt(), msg, null);
      } else {
        logger.info(marker, msg);
      }
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object arg) {
    if (isInfoEnabled()) {
      if (locationAwareLogger != null) {
        log(marker, Level.INFO.toInt(), format, null, arg);
      } else {
        logger.info(marker, format, arg);
      }
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isInfoEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.INFO.toInt(), format, null, arg1, arg2);
      } else {
        logger.info(marker, format, arg1, arg2);
      }
    }
  }

  @Override
  public void info(final Marker marker, final String format, final Object... arguments) {
    if (isInfoEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.INFO.toInt(), format, null, arguments);
      } else {
        logger.info(marker, format, arguments);
      }
    }
  }

  @Override
  public void info(final Marker marker, final String msg, final Throwable t) {
    if (isInfoEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.INFO.toInt(), msg, t);
      } else {
        logger.info(marker, msg, t);
      }
    }
  }

  @Override
  public boolean isWarnEnabled() {
    return logger.isWarnEnabled();
  }

  @Override
  public void warn(final String msg) {
    if (isWarnEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.WARN.toInt(), msg, null);
      } else {
        logger.warn(msg);
      }
    }
  }

  @Override
  public void warn(final String format, final Object arg) {
    if (isWarnEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.WARN.toInt(), format, null, arg);
      } else {
        logger.warn(format, arg);
      }
    }
  }

  @Override
  public void warn(final String format, final Object... arguments) {
    if (isWarnEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.WARN.toInt(), format, null, arguments);
      } else {
        logger.warn(format, arguments);
      }
    }
  }

  @Override
  public void warn(final String format, final Object arg1, final Object arg2) {
    if (isWarnEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.WARN.toInt(), format, null, arg1, arg2);
      } else {
        logger.warn(format, arg1, arg2);
      }
    }
  }

  @Override
  public void warn(final String msg, final Throwable t) {
    if (isWarnEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.WARN.toInt(), msg, t);
      } else {
        logger.warn(msg, t);
      }
    }
  }

  @Override
  public boolean isWarnEnabled(final Marker marker) {
    return logger.isWarnEnabled(marker);
  }

  @Override
  public void warn(final Marker marker, final String msg) {
    if (isWarnEnabled()) {
      if (locationAwareLogger != null) {
        log(marker, Level.WARN.toInt(), msg, null);
      } else {
        logger.warn(marker, msg);
      }
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg) {
    if (isWarnEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.WARN.toInt(), format, null, arg);
      } else {
        logger.warn(marker, format, arg);
      }
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isWarnEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.WARN.toInt(), format, null, arg1, arg2);
      } else {
        logger.warn(marker, format, arg1, arg2);
      }
    }
  }

  @Override
  public void warn(final Marker marker, final String format, final Object... arguments) {
    if (isWarnEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.WARN.toInt(), format, null, arguments);
      } else {
        logger.warn(marker, format, arguments);
      }
    }
  }

  @Override
  public void warn(final Marker marker, final String msg, final Throwable t) {
    if (isWarnEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.WARN.toInt(), msg, t);
      } else {
        logger.warn(marker, msg, t);
      }
    }
  }

  @Override
  public boolean isErrorEnabled() {
    return logger.isErrorEnabled();
  }

  @Override
  public void error(final String msg) {
    if (isErrorEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.ERROR.toInt(), msg, null);
      } else {
        logger.error(msg);
      }
    }
  }

  @Override
  public void error(final String format, final Object arg) {
    if (isErrorEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.ERROR.toInt(), format, null, arg);
      } else {
        logger.error(format, arg);
      }
    }
  }

  @Override
  public void error(final String format, final Object arg1, final Object arg2) {
    if (isErrorEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.ERROR.toInt(), format, null, arg1, arg2);
      } else {
        logger.error(format, arg1, arg2);
      }
    }
  }

  @Override
  public void error(final String format, final Object... arguments) {
    if (isErrorEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.ERROR.toInt(), format, null, arguments);
      } else {
        logger.error(format, arguments);
      }
    }
  }

  @Override
  public void error(final String msg, final Throwable t) {
    if (isErrorEnabled()) {
      if (locationAwareLogger != null) {
        log(null, Level.ERROR.toInt(), msg, t);
      } else {
        logger.error(msg, t);
      }
    }
  }

  @Override
  public boolean isErrorEnabled(final Marker marker) {
    return logger.isErrorEnabled(marker);
  }

  @Override
  public void error(final Marker marker, final String msg) {
    if (isErrorEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.ERROR.toInt(), msg, null);
      } else {
        logger.error(marker, msg);
      }
    }
  }

  @Override
  public void error(final Marker marker, final String format, final Object arg) {
    if (isErrorEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.ERROR.toInt(), format, null, arg);
      } else {
        logger.error(marker, format, arg);
      }
    }
  }

  @Override
  public void error(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    if (isErrorEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.ERROR.toInt(), format, null, arg1, arg2);
      } else {
        logger.error(marker, format, arg1, arg2);
      }
    }
  }

  @Override
  public void error(final Marker marker, final String format, final Object... arguments) {
    if (isErrorEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.ERROR.toInt(), format, null, arguments);
      } else {
        logger.error(marker, format, arguments);
      }
    }
  }

  @Override
  public void error(final Marker marker, final String msg, final Throwable t) {
    if (isErrorEnabled(marker)) {
      if (locationAwareLogger != null) {
        log(marker, Level.ERROR.toInt(), msg, t);
      } else {
        logger.error(marker, msg, t);
      }
    }
  }

  private void log(
      final Marker marker,
      final int level,
      final String message,
      final Throwable throwable,
      final Object... arguments) {
    // there's a bug in Log4J's SLF4J bridge with the implementation of LocationAwareLogger, where
    // the throwable is not properly attached to the statement - there's an open issue for it, but
    // until it is patched we have to do it here ourselves
    // https://issues.apache.org/jira/browse/LOG4J2-2863
    var resolvedThrowable = throwable;
    if (resolvedThrowable == null && arguments != null && arguments.length > 0) {
      final var lastArgument = arguments[arguments.length - 1];
      if (lastArgument instanceof Throwable) {
        resolvedThrowable = (Throwable) lastArgument;
      }
    }

    locationAwareLogger.log(marker, loggerFqcn, level, message, arguments, resolvedThrowable);
  }
}
