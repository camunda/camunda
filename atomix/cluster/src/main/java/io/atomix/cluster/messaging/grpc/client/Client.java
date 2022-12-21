/*
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
package io.atomix.cluster.messaging.grpc.client;

import io.camunda.zeebe.messaging.protocol.MessagingGrpc.MessagingStub;
import io.camunda.zeebe.util.CloseableSilently;
import io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;

record Client(ManagedChannel channel, MessagingStub stub) implements CloseableSilently {

  @Override
  public void close() {
    channel.shutdownNow();
    try {
      // gRPC warns us that channels are not shutdown cleanly if we don't await for their
      // termination before creating the next one. unclear why, but fair enough
      channel.awaitTermination(1, TimeUnit.SECONDS);
    } catch (final InterruptedException ignored) {
      // ignore it for now
    }
  }
}
