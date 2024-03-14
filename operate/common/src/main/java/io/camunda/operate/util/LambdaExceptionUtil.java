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
package io.camunda.operate.util;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Helper class to rethrow checked exceptions in lambda expressions. */
public final class LambdaExceptionUtil {

  /**
   * .forEach(rethrowConsumer(name -> System.out.println(Class.forName(name)))); or
   * .forEach(rethrowConsumer(ClassNameUtil::println));
   */
  public static <T, E extends Exception> Consumer<T> rethrowConsumer(
      ConsumerWithExceptions<T, E> consumer) throws E {
    return t -> {
      try {
        consumer.accept(t);
      } catch (Exception exception) {
        throwAsUnchecked(exception);
      }
    };
  }

  public static <T, U, E extends Exception> BiConsumer<T, U> rethrowBiConsumer(
      BiConsumerWithExceptions<T, U, E> biConsumer) throws E {
    return (t, u) -> {
      try {
        biConsumer.accept(t, u);
      } catch (Exception exception) {
        throwAsUnchecked(exception);
      }
    };
  }

  /** .map(rethrowFunction(name -> Class.forName(name))) or .map(rethrowFunction(Class::forName)) */
  public static <T, R, E extends Exception> Function<T, R> rethrowFunction(
      FunctionWithExceptions<T, R, E> function) throws E {
    return t -> {
      try {
        return function.apply(t);
      } catch (Exception exception) {
        throwAsUnchecked(exception);
        return null;
      }
    };
  }

  /** rethrowSupplier(() -> new StringJoiner(new String(new byte[]{77, 97, 114, 107}, "UTF-8"))), */
  public static <T, E extends Exception> Supplier<T> rethrowSupplier(
      SupplierWithExceptions<T, E> function) throws E {
    return () -> {
      try {
        return function.get();
      } catch (Exception exception) {
        throwAsUnchecked(exception);
        return null;
      }
    };
  }

  /** uncheck(() -> Class.forName("xxx")); */
  public static void uncheck(RunnableWithExceptions t) {
    try {
      t.run();
    } catch (Exception exception) {
      throwAsUnchecked(exception);
    }
  }

  /** uncheck(() -> Class.forName("xxx")); */
  public static <R, E extends Exception> R uncheck(SupplierWithExceptions<R, E> supplier) {
    try {
      return supplier.get();
    } catch (Exception exception) {
      throwAsUnchecked(exception);
      return null;
    }
  }

  /** uncheck(Class::forName, "xxx"); */
  public static <T, R, E extends Exception> R uncheck(
      FunctionWithExceptions<T, R, E> function, T t) {
    try {
      return function.apply(t);
    } catch (Exception exception) {
      throwAsUnchecked(exception);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAsUnchecked(Exception exception) throws E {
    throw (E) exception;
  }

  @FunctionalInterface
  public interface ConsumerWithExceptions<T, E extends Exception> {
    void accept(T t) throws E;
  }

  @FunctionalInterface
  public interface BiConsumerWithExceptions<T, U, E extends Exception> {
    void accept(T t, U u) throws E;
  }

  @FunctionalInterface
  public interface FunctionWithExceptions<T, R, E extends Exception> {
    R apply(T t) throws E;
  }

  @FunctionalInterface
  public interface SupplierWithExceptions<T, E extends Exception> {
    T get() throws E;
  }

  @FunctionalInterface
  public interface RunnableWithExceptions<E extends Exception> {
    void run() throws E;
  }
}
