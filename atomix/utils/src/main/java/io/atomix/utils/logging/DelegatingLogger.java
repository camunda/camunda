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

import static com.google.common.base.MoreObjects.toStringHelper;

import org.slf4j.Logger;
import org.slf4j.Marker;

/** Delegating logger. */
public class DelegatingLogger implements Logger {
  private final Logger delegate;

  public DelegatingLogger(final Logger delegate) {
    this.delegate = delegate;
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
    delegate.trace(msg);
  }

  @Override
  public void trace(final String format, final Object arg) {
    delegate.trace(format, arg);
  }

  @Override
  public void trace(final String format, final Object arg1, final Object arg2) {
    delegate.trace(format, arg1, arg2);
  }

  @Override
  public void trace(final String format, final Object... arguments) {
    delegate.trace(format, arguments);
  }

  @Override
  public void trace(final String msg, final Throwable t) {
    delegate.trace(msg, t);
  }

  @Override
  public boolean isTraceEnabled(final Marker marker) {
    return delegate.isTraceEnabled(marker);
  }

  @Override
  public void trace(final Marker marker, final String msg) {
    delegate.trace(marker, msg);
  }

  @Override
  public void trace(final Marker marker, final String format, final Object arg) {
    delegate.trace(marker, format, arg);
  }

  @Override
  public void trace(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    delegate.trace(marker, format, arg1, arg2);
  }

  @Override
  public void trace(final Marker marker, final String format, final Object... argArray) {
    delegate.trace(marker, format, argArray);
  }

  @Override
  public void trace(final Marker marker, final String msg, final Throwable t) {
    delegate.trace(marker, msg, t);
  }

  @Override
  public boolean isDebugEnabled() {
    return delegate.isDebugEnabled();
  }

  @Override
  public void debug(final String msg) {
    delegate.debug(msg);
  }

  @Override
  public void debug(final String format, final Object arg) {
    delegate.debug(format, arg);
  }

  @Override
  public void debug(final String format, final Object arg1, final Object arg2) {
    delegate.debug(format, arg1, arg2);
  }

  @Override
  public void debug(final String format, final Object... arguments) {
    delegate.debug(format, arguments);
  }

  @Override
  public void debug(final String msg, final Throwable t) {
    delegate.debug(msg, t);
  }

  @Override
  public boolean isDebugEnabled(final Marker marker) {
    return delegate.isDebugEnabled(marker);
  }

  @Override
  public void debug(final Marker marker, final String msg) {
    delegate.debug(marker, msg);
  }

  @Override
  public void debug(final Marker marker, final String format, final Object arg) {
    delegate.debug(marker, format, arg);
  }

  @Override
  public void debug(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    delegate.debug(marker, format, arg1, arg2);
  }

  @Override
  public void debug(final Marker marker, final String format, final Object... arguments) {
    delegate.debug(marker, format, arguments);
  }

  @Override
  public void debug(final Marker marker, final String msg, final Throwable t) {
    delegate.debug(marker, msg, t);
  }

  @Override
  public boolean isInfoEnabled() {
    return delegate.isInfoEnabled();
  }

  @Override
  public void info(final String msg) {
    delegate.info(msg);
  }

  @Override
  public void info(final String format, final Object arg) {
    delegate.info(format, arg);
  }

  @Override
  public void info(final String format, final Object arg1, final Object arg2) {
    delegate.info(format, arg1, arg2);
  }

  @Override
  public void info(final String format, final Object... arguments) {
    delegate.info(format, arguments);
  }

  @Override
  public void info(final String msg, final Throwable t) {
    delegate.info(msg, t);
  }

  @Override
  public boolean isInfoEnabled(final Marker marker) {
    return delegate.isInfoEnabled(marker);
  }

  @Override
  public void info(final Marker marker, final String msg) {
    delegate.info(marker, msg);
  }

  @Override
  public void info(final Marker marker, final String format, final Object arg) {
    delegate.info(marker, format, arg);
  }

  @Override
  public void info(final Marker marker, final String format, final Object arg1, final Object arg2) {
    delegate.info(marker, format, arg1, arg2);
  }

  @Override
  public void info(final Marker marker, final String format, final Object... arguments) {
    delegate.info(marker, format, arguments);
  }

  @Override
  public void info(final Marker marker, final String msg, final Throwable t) {
    delegate.info(marker, msg, t);
  }

  @Override
  public boolean isWarnEnabled() {
    return delegate.isWarnEnabled();
  }

  @Override
  public void warn(final String msg) {
    delegate.warn(msg);
  }

  @Override
  public void warn(final String format, final Object arg) {
    delegate.warn(format, arg);
  }

  @Override
  public void warn(final String format, final Object... arguments) {
    delegate.warn(format, arguments);
  }

  @Override
  public void warn(final String format, final Object arg1, final Object arg2) {
    delegate.warn(format, arg1, arg2);
  }

  @Override
  public void warn(final String msg, final Throwable t) {
    delegate.warn(msg, t);
  }

  @Override
  public boolean isWarnEnabled(final Marker marker) {
    return delegate.isWarnEnabled(marker);
  }

  @Override
  public void warn(final Marker marker, final String msg) {
    delegate.warn(marker, msg);
  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg) {
    delegate.warn(marker, format, arg);
  }

  @Override
  public void warn(final Marker marker, final String format, final Object arg1, final Object arg2) {
    delegate.warn(marker, format, arg1, arg2);
  }

  @Override
  public void warn(final Marker marker, final String format, final Object... arguments) {
    delegate.warn(marker, format, arguments);
  }

  @Override
  public void warn(final Marker marker, final String msg, final Throwable t) {
    delegate.warn(marker, msg, t);
  }

  @Override
  public boolean isErrorEnabled() {
    return delegate.isErrorEnabled();
  }

  @Override
  public void error(final String msg) {
    delegate.error(msg);
  }

  @Override
  public void error(final String format, final Object arg) {
    delegate.error(format, arg);
  }

  @Override
  public void error(final String format, final Object arg1, final Object arg2) {
    delegate.error(format, arg1, arg2);
  }

  @Override
  public void error(final String format, final Object... arguments) {
    delegate.error(format, arguments);
  }

  @Override
  public void error(final String msg, final Throwable t) {
    delegate.error(msg, t);
  }

  @Override
  public boolean isErrorEnabled(final Marker marker) {
    return delegate.isErrorEnabled(marker);
  }

  @Override
  public void error(final Marker marker, final String msg) {
    delegate.error(marker, msg);
  }

  @Override
  public void error(final Marker marker, final String format, final Object arg) {
    delegate.error(marker, format, arg);
  }

  @Override
  public void error(
      final Marker marker, final String format, final Object arg1, final Object arg2) {
    delegate.error(marker, format, arg1, arg2);
  }

  @Override
  public void error(final Marker marker, final String format, final Object... arguments) {
    delegate.error(marker, format, arguments);
  }

  @Override
  public void error(final Marker marker, final String msg, final Throwable t) {
    delegate.error(marker, msg, t);
  }

  @Override
  public String toString() {
    return toStringHelper(this).addValue(delegate).toString();
  }
}
