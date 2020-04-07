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

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.function.Supplier;

/** Logger context. */
public class LoggerContext {

  private final Supplier<String> stringProvider;

  public LoggerContext(final Supplier<String> stringProvider) {
    this.stringProvider = stringProvider;
  }

  /**
   * Returns a new contextual logger builder.
   *
   * @param name the logger name
   * @return the logger builder
   */
  public static Builder builder(final String name) {
    return new Builder(name);
  }

  /**
   * Returns a new contextual logger builder.
   *
   * @param clazz the logger class
   * @return the logger builder
   */
  public static Builder builder(final Class clazz) {
    return new Builder(clazz.getSimpleName());
  }

  @Override
  public String toString() {
    return stringProvider.get();
  }

  /** Contextual logger builder. */
  public static class Builder implements io.atomix.utils.Builder<LoggerContext> {
    private final MoreObjects.ToStringHelper identityStringHelper;
    private MoreObjects.ToStringHelper argsStringHelper;
    private boolean omitNullValues = false;

    public Builder(final String name) {
      this.identityStringHelper = MoreObjects.toStringHelper(name);
    }

    /** Initializes the arguments string helper. */
    private void initializeArgs() {
      if (argsStringHelper == null) {
        argsStringHelper = MoreObjects.toStringHelper("");
      }
    }

    /**
     * Configures the {@link MoreObjects.ToStringHelper} so {@link #toString()} will ignore
     * properties with null value. The order of calling this method, relative to the {@code
     * add()}/{@code addValue()} methods, is not significant.
     */
    @CanIgnoreReturnValue
    public Builder omitNullValues() {
      this.omitNullValues = true;
      return this;
    }

    /**
     * Adds a name/value pair to the formatted output in {@code name=value} format. If {@code value}
     * is {@code null}, the string {@code "null"} is used, unless {@link #omitNullValues()} is
     * called, in which case this name/value pair will not be added.
     */
    @CanIgnoreReturnValue
    public Builder add(final String name, final Object value) {
      initializeArgs();
      argsStringHelper.add(name, value);
      return this;
    }

    /** Adds a name/value pair to the formatted output in {@code name=value} format. */
    @CanIgnoreReturnValue
    public Builder add(final String name, final boolean value) {
      initializeArgs();
      argsStringHelper.add(name, value);
      return this;
    }

    /** Adds a name/value pair to the formatted output in {@code name=value} format. */
    @CanIgnoreReturnValue
    public Builder add(final String name, final char value) {
      initializeArgs();
      argsStringHelper.add(name, value);
      return this;
    }

    /** Adds a name/value pair to the formatted output in {@code name=value} format. */
    @CanIgnoreReturnValue
    public Builder add(final String name, final double value) {
      initializeArgs();
      argsStringHelper.add(name, value);
      return this;
    }

    /** Adds a name/value pair to the formatted output in {@code name=value} format. */
    @CanIgnoreReturnValue
    public Builder add(final String name, final float value) {
      initializeArgs();
      argsStringHelper.add(name, value);
      return this;
    }

    /** Adds a name/value pair to the formatted output in {@code name=value} format. */
    @CanIgnoreReturnValue
    public Builder add(final String name, final int value) {
      initializeArgs();
      argsStringHelper.add(name, value);
      return this;
    }

    /** Adds a name/value pair to the formatted output in {@code name=value} format. */
    @CanIgnoreReturnValue
    public Builder add(final String name, final long value) {
      initializeArgs();
      argsStringHelper.add(name, value);
      return this;
    }

    /**
     * Adds an unnamed value to the formatted output.
     *
     * <p>It is strongly encouraged to use {@link #add(String, Object)} instead and give value a
     * readable name.
     */
    @CanIgnoreReturnValue
    public Builder addValue(final Object value) {
      identityStringHelper.addValue(value);
      return this;
    }

    /**
     * Adds an unnamed value to the formatted output.
     *
     * <p>It is strongly encouraged to use {@link #add(String, boolean)} instead and give value a
     * readable name.
     */
    @CanIgnoreReturnValue
    public Builder addValue(final boolean value) {
      identityStringHelper.addValue(value);
      return this;
    }

    /**
     * Adds an unnamed value to the formatted output.
     *
     * <p>It is strongly encouraged to use {@link #add(String, char)} instead and give value a
     * readable name.
     */
    @CanIgnoreReturnValue
    public Builder addValue(final char value) {
      identityStringHelper.addValue(value);
      return this;
    }

    /**
     * Adds an unnamed value to the formatted output.
     *
     * <p>It is strongly encouraged to use {@link #add(String, double)} instead and give value a
     * readable name.
     */
    @CanIgnoreReturnValue
    public Builder addValue(final double value) {
      identityStringHelper.addValue(value);
      return this;
    }

    /**
     * Adds an unnamed value to the formatted output.
     *
     * <p>It is strongly encouraged to use {@link #add(String, float)} instead and give value a
     * readable name.
     */
    @CanIgnoreReturnValue
    public Builder addValue(final float value) {
      identityStringHelper.addValue(value);
      return this;
    }

    /**
     * Adds an unnamed value to the formatted output.
     *
     * <p>It is strongly encouraged to use {@link #add(String, int)} instead and give value a
     * readable name.
     */
    @CanIgnoreReturnValue
    public Builder addValue(final int value) {
      identityStringHelper.addValue(value);
      return this;
    }

    /**
     * Adds an unnamed value to the formatted output.
     *
     * <p>It is strongly encouraged to use {@link #add(String, long)} instead and give value a
     * readable name.
     */
    @CanIgnoreReturnValue
    public Builder addValue(final long value) {
      identityStringHelper.addValue(value);
      return this;
    }

    @Override
    public LoggerContext build() {
      final MoreObjects.ToStringHelper identityStringHelper = this.identityStringHelper;
      final MoreObjects.ToStringHelper argsStringHelper = this.argsStringHelper;
      if (omitNullValues) {
        identityStringHelper.omitNullValues();
        if (argsStringHelper != null) {
          argsStringHelper.omitNullValues();
        }
      }
      return new LoggerContext(
          () -> {
            if (argsStringHelper == null) {
              return identityStringHelper.toString();
            } else {
              return identityStringHelper.toString() + argsStringHelper.toString();
            }
          });
    }
  }
}
