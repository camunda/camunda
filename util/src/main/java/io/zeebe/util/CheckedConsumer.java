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

import java.util.Objects;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface CheckedConsumer<T> {
  void accept(T t) throws Exception;

  /**
   * @param nextConsumer only invoked if this consumer is successful
   * @return a combined consumer that first invokes this consumer and then the parameter
   */
  default CheckedConsumer<T> andThen(CheckedConsumer<T> nextConsumer) {
    Objects.requireNonNull(nextConsumer);
    return (T t) -> {
      this.accept(t);
      nextConsumer.accept(t);
    };
  }

  default CheckedConsumer<T> andOnException(BiConsumer<T, Exception> exceptionHandler) {
    Objects.requireNonNull(exceptionHandler);
    return (T t) -> {
      try {
        this.accept(t);
      } catch (Exception e) {
        exceptionHandler.accept(t, e);
      }
    };
  }

  default CheckedConsumer<T> andOnExceptionRetry(
      int retries, BiConsumer<T, Exception> retryExceptionHandler) {
    EnsureUtil.ensureGreaterThanOrEqual("times", retries, 0);
    Objects.requireNonNull(retryExceptionHandler);
    return (T t) -> {
      int invocation = -1;

      do {
        invocation++;
        try {
          this.accept(t);
          return;
        } catch (Exception e) {
          if (invocation < retries) {
            retryExceptionHandler.accept(t, e);
          } else {
            throw e;
          }
        }
      } while (invocation < retries);
    };
  }
}
