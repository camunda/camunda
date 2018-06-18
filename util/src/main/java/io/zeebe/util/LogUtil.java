/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.MDC;

public class LogUtil {
  /** see https://logback.qos.ch/manual/mdc.html */
  public static void doWithMDC(Map<String, String> context, Runnable r) {
    final Map<String, String> currentContext = MDC.getCopyOfContextMap();
    MDC.setContextMap(context);

    try {
      r.run();
    } finally {
      if (currentContext != null) {
        MDC.setContextMap(currentContext);
      } else {
        MDC.clear();
      }
    }
  }

  public static void catchAndLog(Logger log, String msg, ThrowingRunnable r) {
    try {
      r.run();
    } catch (Throwable e) {
      log.error(msg, e);
    }
  }

  public static void catchAndLog(Logger log, ThrowingRunnable r) {
    try {
      r.run();
    } catch (Throwable e) {
      log.error(e.getMessage(), e);
    }
  }

  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Exception;
  }
}
