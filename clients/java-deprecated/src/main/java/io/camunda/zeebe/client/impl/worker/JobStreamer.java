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
package io.camunda.zeebe.client.impl.worker;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.function.Consumer;
import net.jcip.annotations.ThreadSafe;

@ThreadSafe
interface JobStreamer extends AutoCloseable {

  @Override
  void close();

  boolean isOpen();

  void openStreamer(final Consumer<ActivatedJob> jobConsumer);

  static JobStreamer noop() {
    return NoopJobStream.Singleton.INSTANCE.stream;
  }

  @ThreadSafe
  final class NoopJobStream implements JobStreamer {

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
      return false;
    }

    @Override
    public void openStreamer(final Consumer<ActivatedJob> jobConsumer) {}

    private enum Singleton {
      @SuppressWarnings("resource")
      INSTANCE(new NoopJobStream());

      private final NoopJobStream stream;

      Singleton(final NoopJobStream stream) {
        this.stream = stream;
      }
    }
  }
}
