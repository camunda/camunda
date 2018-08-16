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
package io.zeebe.test.broker.protocol.managementApi;

import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

public class ManagementApiRule extends ExternalResource {

  private ClientTransport transport;

  private final Supplier<SocketAddress> brokerAddressSupplier;
  private RemoteAddress remoteAddress;

  private ControlledActorClock controlledActorClock = new ControlledActorClock();
  private ActorScheduler scheduler;

  public ManagementApiRule() {
    this(() -> new SocketAddress("localhost", 26502));
  }

  public ManagementApiRule(Supplier<SocketAddress> brokerAddressSupplier) {
    this.brokerAddressSupplier = brokerAddressSupplier;
  }

  @Override
  protected void before() throws Throwable {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setActorClock(controlledActorClock)
            .build();
    scheduler.start();

    transport = Transports.newClientTransport().scheduler(scheduler).build();

    remoteAddress = transport.registerRemoteAddress(brokerAddressSupplier.get());
  }

  @Override
  protected void after() {
    if (transport != null) {
      transport.close();
    }

    if (scheduler != null) {
      scheduler.stop();
    }
  }

  public void interruptAllChannels() {
    transport.interruptAllChannels();
  }

  public SocketAddress getBrokerAddress() {
    return brokerAddressSupplier.get();
  }

  public ClientTransport getTransport() {
    return transport;
  }

  public ControlledActorClock getClock() {
    return controlledActorClock;
  }

  public ActorFuture<ClientResponse> send(BufferWriter request) {
    return transport.getOutput().sendRequest(remoteAddress, request);
  }

  public void sendAndAwait(BufferWriter request, BufferReader response) {
    final ActorFuture<ClientResponse> responseFuture = send(request);

    final ClientResponse clientResponse = responseFuture.join();

    final DirectBuffer responseBuffer = clientResponse.getResponseBuffer();
    response.wrap(responseBuffer, 0, responseBuffer.capacity());
  }
}
