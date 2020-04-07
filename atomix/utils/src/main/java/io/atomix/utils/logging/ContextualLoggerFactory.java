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

import org.slf4j.LoggerFactory;

/** Contextual logger factory. */
public final class ContextualLoggerFactory {

  private ContextualLoggerFactory() {}

  /**
   * Returns a contextual logger.
   *
   * @param name the contextual logger name
   * @param context the logger context
   * @return the logger
   */
  public static ContextualLogger getLogger(final String name, final LoggerContext context) {
    return new ContextualLogger(LoggerFactory.getLogger(name), context);
  }

  /**
   * Returns a contextual logger.
   *
   * @param clazz the contextual logger class
   * @param context the logger context
   * @return the logger
   */
  public static ContextualLogger getLogger(final Class clazz, final LoggerContext context) {
    return new ContextualLogger(LoggerFactory.getLogger(clazz), context);
  }
}
