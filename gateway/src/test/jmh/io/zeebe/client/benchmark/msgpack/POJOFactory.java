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
package io.zeebe.client.benchmark.msgpack;

import java.util.HashMap;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public interface POJOFactory {
  DirectBuffer PAYLOAD = new UnsafeBuffer(new byte[1024 * 1]);

  TaskEvent build();

  String getDescription();

  Type getType();

  enum Type {
    REUSE_POJO_REUSE_PROPERTIES,
    REUSE_POJO_INSTANTIATE_PROPERTIES,
    INSTANTIATE_POJO_INSTANTIATE_PROPERTIES
  }

  class ReuseObjectAndPropertiesFactory implements POJOFactory {
    protected TaskEvent instance;

    public ReuseObjectAndPropertiesFactory(TaskEvent instance) {
      this.instance = instance;
      populatePOJO(instance);
    }

    @Override
    public TaskEvent build() {
      return instance;
    }

    @Override
    public String getDescription() {
      return "instantiate POJO 1 time, populate POJO 1 time";
    }

    @Override
    public Type getType() {
      return Type.REUSE_POJO_REUSE_PROPERTIES;
    }
  }

  class ReuseObjectFactory implements POJOFactory {
    protected TaskEvent instance;

    public ReuseObjectFactory(TaskEvent instance) {
      this.instance = instance;
    }

    @Override
    public TaskEvent build() {
      populatePOJO(instance);
      return instance;
    }

    @Override
    public String getDescription() {
      return "instantiate POJO 1 time, populate POJO n times";
    }

    @Override
    public Type getType() {
      return Type.REUSE_POJO_INSTANTIATE_PROPERTIES;
    }
  }

  class InstantiateAlwaysFactory implements POJOFactory {
    protected Supplier<TaskEvent> instanceSupplier;

    public InstantiateAlwaysFactory(Supplier<TaskEvent> instanceSupplier) {
      this.instanceSupplier = instanceSupplier;
    }

    @Override
    public TaskEvent build() {
      final TaskEvent instance = instanceSupplier.get();
      populatePOJO(instance);
      return instance;
    }

    @Override
    public String getDescription() {
      return "instantiate POJO n times, populate POJO n times";
    }

    @Override
    public Type getType() {
      return Type.INSTANTIATE_POJO_INSTANTIATE_PROPERTIES;
    }
  }

  static void populatePOJO(TaskEvent event) {
    event.setEventType(TaskEventType.ABORT_FAILED);
    final HashMap<String, String> headers = new HashMap<>();
    headers.put("key1", "val1");
    headers.put("key2", "val3");
    headers.put("key3", "val3");
    event.setHeaders(headers);
    event.setLockTime(123123123L);
    event.setPayload(PAYLOAD.byteArray());
    event.setType("foofoobarbaz");
  }
}
