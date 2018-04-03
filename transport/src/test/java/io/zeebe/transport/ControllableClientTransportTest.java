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
package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;

public class ControllableClientTransportTest
{

    private static final int MAX_ITERATION = 1000;
    public static final DirectBuffer BUF1 = new UnsafeBuffer(new byte[32]);
    public static final SocketAddress SERVER_ADDRESS1 = new SocketAddress("localhost", 51115);

    public static final int SEND_BUFFER_SIZE = 16 * 1024;

    public static final int MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER = SEND_BUFFER_SIZE / BUF1.capacity();

    public ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(scheduler).around(closeables);

    protected ClientTransport clientTransport;
    private Dispatcher clientSendBuffer;

    @Before
    public void setUp()
    {
        clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .actorScheduler(scheduler.get())
            .build();
        managedCloseableControlled(() -> clientSendBuffer.closeAsync());

        clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .requestPoolSize(MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER + 1)
            .scheduler(scheduler.get())
            .build();
        managedCloseableControlled(() -> clientTransport.closeAsync());
    }

    private void managedCloseableControlled(Supplier<ActorFuture<Void>> closeable)
    {
        closeables.manage(() ->
        {
            final ActorFuture<Void> closeFuture = closeable.get();
            int iteration = 0;
            do
            {
                scheduler.workUntilDone();
                iteration++;
            }
            while (!closeFuture.isDone() && iteration < MAX_ITERATION);

            if (!closeFuture.isDone())
            {
                throw new RuntimeException("Could not close closeable");
            }
        });
    }

    /**
     * <p>The point of this test is that the request is submitted before any actor work is done in the client
     *
     * <p>Expected behavior: Request timeout applies as usual
     *
     * <p>Undesired behavior: Request timeout did not apply, because the request controller performed the request in
     * the STARTING actor phase
     */
    @Test
    public void shouldTimeOutRequestWhenSubmittedImmediately()
    {
        // given
        scheduler.getClock().pinCurrentTime();

        final ClientOutput output = clientTransport.getOutput();
        final Duration timeout = Duration.ofSeconds(35);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

        final ActorFuture<ClientResponse> responseFuture =
                output.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1), timeout);

        scheduler.workUntilDone();

        // when
        scheduler.getClock().addTime(timeout.plusSeconds(1));

        final int schedulerTimerWheelResolution = 32;
        // => workaround for https://github.com/zeebe-io/zeebe/issues/767
        for (int i = 0; i < schedulerTimerWheelResolution; i++)
        {
            scheduler.workUntilDone();
        }

        // then
        assertThat(responseFuture).isDone();
        assertThatThrownBy(() -> responseFuture.get()).hasMessageContaining("Request timed out");

    }

}
