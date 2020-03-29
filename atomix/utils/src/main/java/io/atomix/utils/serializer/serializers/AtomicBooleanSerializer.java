/*
 * Copyright 2014-present Open Networking Foundation
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
package io.atomix.utils.serializer.serializers;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.util.concurrent.atomic.AtomicBoolean;

public class AtomicBooleanSerializer extends Serializer<AtomicBoolean> {

  @Override
  public void write(final Kryo kryo, final Output output, final AtomicBoolean object) {
    output.writeBoolean(object.get());
  }

  @Override
  public AtomicBoolean read(final Kryo kryo, final Input input, final Class<AtomicBoolean> type) {
    return new AtomicBoolean(input.readBoolean());
  }
}
