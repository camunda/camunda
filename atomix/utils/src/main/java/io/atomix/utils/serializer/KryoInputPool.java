/*
 * Copyright 2014-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.utils.serializer;

import com.esotericsoftware.kryo.io.Input;

class KryoInputPool extends KryoIOPool<Input> {

  static final int MAX_POOLED_BUFFER_SIZE = 512 * 1024;

  @Override
  protected Input create(final int bufferSize) {
    return new Input(bufferSize);
  }

  @Override
  protected boolean recycle(final Input input) {
    if (input.getBuffer().length < MAX_POOLED_BUFFER_SIZE) {
      input.setInputStream(null);
      return true;
    }
    return false; // discard
  }
}
