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
package io.zeebe.util.sched;

import io.zeebe.util.LangUtil;
import java.util.concurrent.Future;

public class FutureUtil {
  /**
   * Invokes Future.get() returning the result of the invocation. Transforms checked exceptions into
   * RuntimeExceptions to accommodate programmer laziness.
   */
  public static <T> T join(Future<T> f) {
    try {
      return f.get();
    } catch (Exception e) {
      // NOTE: here we actually want to use rethrowUnchecked
      LangUtil.rethrowUnchecked(e);
    }

    return null;
  }

  public static Runnable wrap(Future<?> future) {
    return () -> {
      try {
        future.get();
      } catch (Exception e) {
        LangUtil.rethrowUnchecked(e);
      }
    };
  }
}
