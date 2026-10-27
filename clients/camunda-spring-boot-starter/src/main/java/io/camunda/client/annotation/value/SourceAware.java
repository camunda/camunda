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
package io.camunda.client.annotation.value;

public sealed interface SourceAware<T> {
  T value();

  int priority();

  boolean generated();

  record Empty<T>() implements SourceAware<T> {

    @Override
    public T value() {
      return null;
    }

    @Override
    public int priority() {
      return 0;
    }

    @Override
    public boolean generated() {
      return true;
    }
  }

  record GeneratedFromMethodInfo<T>(T value) implements SourceAware<T> {

    @Override
    public int priority() {
      return 1;
    }

    @Override
    public boolean generated() {
      return true;
    }
  }

  record FromDefaultProperty<T>(T value) implements SourceAware<T> {
    @Override
    public int priority() {
      return 2;
    }

    @Override
    public boolean generated() {
      return false;
    }
  }

  record FromAnnotation<T>(T value) implements SourceAware<T> {
    @Override
    public int priority() {
      return 3;
    }

    @Override
    public boolean generated() {
      return false;
    }
  }

  record FromOverrideProperty<T>(T value) implements SourceAware<T> {
    @Override
    public int priority() {
      return 4;
    }

    @Override
    public boolean generated() {
      return false;
    }
  }

  record FromRuntimeOverride<T>(T value, SourceAware<T> original) implements SourceAware<T> {
    @Override
    public int priority() {
      return 5;
    }

    @Override
    public boolean generated() {
      return false;
    }
  }

  record FromLegacy<T>(T value) implements SourceAware<T> {
    @Override
    public int priority() {
      return -1;
    }

    @Override
    public boolean generated() {
      return false;
    }
  }
}
