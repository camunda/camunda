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
package io.camunda.tasklist.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//
// Taken from https://stackoverflow.com/questions/13239972/how-do-you-implement-a-re-try-catch and
// slightly modified
//
public final class RetryOperation<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RetryOperation.class);

  private RetryConsumer<T> retryConsumer;
  private int noOfRetry;
  private int delayInterval;
  private TimeUnit timeUnit;
  private RetryPredicate<T> retryPredicate;
  private List<Class<? extends Throwable>> exceptionList;
  private String message;

  private RetryOperation(
      RetryConsumer<T> retryConsumer,
      int noOfRetry,
      int delayInterval,
      TimeUnit timeUnit,
      RetryPredicate<T> retryPredicate,
      List<Class<? extends Throwable>> exceptionList,
      String message) {
    this.retryConsumer = retryConsumer;
    this.noOfRetry = noOfRetry;
    this.delayInterval = delayInterval;
    this.timeUnit = timeUnit;
    this.retryPredicate = retryPredicate;
    this.exceptionList = exceptionList;
    this.message = message;
  }

  public static <T> OperationBuilder<T> newBuilder() {
    return new OperationBuilder<>();
  }

  public T retry() throws Exception {
    T result = null;
    int retries = 0;
    while (retries < noOfRetry) {
      try {
        result = retryConsumer.evaluate();
        if (Objects.nonNull(retryPredicate)) {
          final boolean shouldItRetry = retryPredicate.shouldRetry(result);
          if (shouldItRetry) {
            retries = increaseRetryCountAndSleep(retries);
          } else {
            return result;
          }
        } else {
          // no retry condition defined, no exception thrown. This is the desired result.
          return result;
        }
      } catch (Exception e) {
        LOGGER.warn(String.format("Retry Operation %s failed: %s", message, e.getMessage()), e);
        retries = handleException(retries, e);
      }
    }
    return result;
  }

  private int handleException(int retries, Exception e) throws Exception {
    if (exceptionList.isEmpty()
        || exceptionList.stream().anyMatch(ex -> ex.isAssignableFrom(e.getClass()))) {
      // exception is accepted, continue retry.
      retries = increaseRetryCountAndSleep(retries);
      if (retries == noOfRetry) {
        // evaluation is throwing exception, no more retry left. Throw it.
        throw e;
      }
    } else {
      // unexpected exception, no retry required. Throw it.
      throw e;
    }
    return retries;
  }

  private int increaseRetryCountAndSleep(int retries) {
    retries++;
    if (retries < noOfRetry && delayInterval > 0) {
      try {
        if (retries % 20 == 0) {
          LOGGER.info(
              "{} - Waiting {} {}. {}/{}", message, delayInterval, timeUnit, retries, noOfRetry);
        } else {
          LOGGER.debug(
              "{} - Waiting {} {}. {}/{}", message, delayInterval, timeUnit, retries, noOfRetry);
        }
        timeUnit.sleep(delayInterval);
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
    }
    return retries;
  }

  public static final class OperationBuilder<T> {
    private RetryConsumer<T> iRetryConsumer;
    private int iNoOfRetry;
    private int iDelayInterval;
    private TimeUnit iTimeUnit;
    private RetryPredicate<T> iRetryPredicate;
    private Class<? extends Throwable>[] exceptionClasses;
    private String message = "";

    private OperationBuilder() {}

    public OperationBuilder<T> retryConsumer(final RetryConsumer<T> retryConsumer) {
      this.iRetryConsumer = retryConsumer;
      return this;
    }

    public OperationBuilder<T> noOfRetry(final int noOfRetry) {
      this.iNoOfRetry = noOfRetry;
      return this;
    }

    public OperationBuilder<T> delayInterval(final int delayInterval, final TimeUnit timeUnit) {
      this.iDelayInterval = delayInterval;
      this.iTimeUnit = timeUnit;
      return this;
    }

    public OperationBuilder<T> retryPredicate(final RetryPredicate<T> retryPredicate) {
      this.iRetryPredicate = retryPredicate;
      return this;
    }

    @SafeVarargs
    public final OperationBuilder<T> retryOn(final Class<? extends Throwable>... exceptionClasses) {
      this.exceptionClasses = exceptionClasses;
      return this;
    }

    public OperationBuilder<T> message(final String message) {
      this.message = message;
      return this;
    }

    public RetryOperation<T> build() {
      if (Objects.isNull(iRetryConsumer)) {
        throw new RuntimeException("'#retryConsumer:RetryConsumer<T>' not set");
      }

      List<Class<? extends Throwable>> exceptionList = new ArrayList<>();
      if (Objects.nonNull(exceptionClasses) && exceptionClasses.length > 0) {
        exceptionList = Arrays.asList(exceptionClasses);
      }
      iNoOfRetry = iNoOfRetry == 0 ? 1 : iNoOfRetry;
      iTimeUnit = Objects.isNull(iTimeUnit) ? TimeUnit.MILLISECONDS : iTimeUnit;
      return new RetryOperation<>(
          iRetryConsumer,
          iNoOfRetry,
          iDelayInterval,
          iTimeUnit,
          iRetryPredicate,
          exceptionList,
          message);
    }
  }

  public static interface RetryConsumer<T> {
    T evaluate() throws Exception;
  }

  public static interface RetryPredicate<T> {
    boolean shouldRetry(T t);
  }
}
