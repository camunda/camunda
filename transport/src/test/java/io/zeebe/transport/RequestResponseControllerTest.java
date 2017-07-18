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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.zeebe.test.util.BufferAssert;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.io.FailingBufferWriter;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.DirectBufferReader;
import io.zeebe.util.buffer.DirectBufferWriter;

@RunWith(MockitoJUnitRunner.class)
public class RequestResponseControllerTest
{
    protected static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
    protected static final DirectBuffer BUF2 = BufferUtil.wrapBytes(5, 6, 7, 8);

    protected static final SocketAddress RECEIVER = new SocketAddress("123.123.123.123", 1234);
    protected static final RemoteAddress REMOTE = new RemoteAddress(123, RECEIVER);

    @Mock
    protected ClientTransport transport;
    protected MockClientOutput output;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp()
    {
        output = new MockClientOutput();

        when(transport.getOutput()).thenReturn(output);
        when(transport.registerRemoteAddress(any())).thenReturn(REMOTE);
    }

    @Test
    public void shouldSendRequest() throws Exception
    {
        // given
        final RequestResponseController rrController = new RequestResponseController(transport);

        final ClientRequest request = mock(ClientRequest.class);
        output.addStubRequests(request);

        when(request.isDone()).thenReturn(true);
        when(request.get()).thenReturn(BUF1);
        final DirectBufferReader responseReader = new DirectBufferReader();

        rrController.open(RECEIVER, new DirectBufferWriter().wrap(BUF2), responseReader);

        // when
        TestUtil.doRepeatedly(() -> rrController.doWork())
            .until(i -> rrController.isResponseAvailable());

        // then
        assertThat(rrController.isResponseAvailable()).isTrue();
        assertThat(rrController.isFailed()).isFalse();
        assertThat(rrController.isClosed()).isFalse();

        assertThat(rrController.getResponseLength()).isEqualTo(BUF1.capacity());
        BufferAssert.assertThatBuffer(rrController.getResponseBuffer()).hasBytes(BUF1);
        BufferAssert.assertThatBuffer(responseReader.getBuffer()).hasBytes(BUF1);
    }

    @Test
    public void shouldSendRequestInCaseOfTemporaryBackpressure() throws Exception
    {
        // given
        final RequestResponseController rrController = new RequestResponseController(transport);
        final ClientRequest request = mock(ClientRequest.class);

        // first two requests cannot be served
        output.addStubRequests(null, null, request);

        when(request.isDone()).thenReturn(true);
        when(request.get()).thenReturn(BUF1);
        final DirectBufferReader responseReader = new DirectBufferReader();

        rrController.open(RECEIVER, new DirectBufferWriter().wrap(BUF2), responseReader);

        // when
        TestUtil.doRepeatedly(() -> rrController.doWork())
            .until(i -> rrController.isResponseAvailable());

        // then
        assertThat(rrController.isResponseAvailable()).isTrue();
    }

    @Test
    public void shouldFailOnNonSendableRequest()
    {
        // given
        final RequestResponseController rrController = new RequestResponseController(transport);
        final ClientRequest request = mock(ClientRequest.class);
        output.addStubRequests(request);

        final BufferReader responseReader = mock(BufferReader.class);
        rrController.open(RECEIVER, new FailingBufferWriter(), responseReader);

        // when
        TestUtil.doRepeatedly(() -> rrController.doWork())
            .until(i -> rrController.isFailed());

        // then
        assertThat(rrController.isResponseAvailable()).isFalse();
        assertThat(rrController.isFailed()).isTrue();
        assertThat(rrController.isClosed()).isFalse();

        final Exception failure = rrController.getFailure();

        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure).hasMessage("Could not write - expected");
    }

    @Test
    public void shouldFailOnNonReadableResponse() throws Exception
    {
        // given
        final RequestResponseController rrController = new RequestResponseController(transport);

        final ClientRequest request = mock(ClientRequest.class);
        when(request.isDone()).thenReturn(true);
        when(request.get()).thenReturn(BUF1);
        output.addStubRequests(request);

        rrController.open(
            RECEIVER,
            new DirectBufferWriter().wrap(BUF1),
            (b, o, l) ->
            {
                throw new RuntimeException("does not read");
            });

        // when
        TestUtil.doRepeatedly(() -> rrController.doWork())
            .until(i -> rrController.isFailed());

        // then
        assertThat(rrController.isResponseAvailable()).isFalse();
        assertThat(rrController.isFailed()).isTrue();
        assertThat(rrController.isClosed()).isFalse();

        final Exception failure = rrController.getFailure();

        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure).hasMessage("does not read");
    }

    @Test
    public void shouldCloseAfterResponseReceived() throws Exception
    {
        // given
        final RequestResponseController rrController = new RequestResponseController(transport);

        final ClientRequest request = mock(ClientRequest.class);
        when(request.isDone()).thenReturn(true);
        when(request.get()).thenReturn(BUF1);
        output.addStubRequests(request);

        rrController.open(RECEIVER, new DirectBufferWriter().wrap(BUF1), null);

        TestUtil.doRepeatedly(() -> rrController.doWork())
            .until(i -> rrController.isResponseAvailable());

        // when
        rrController.close();
        TestUtil.doRepeatedly(() -> rrController.doWork()).until(i -> rrController.isClosed());

        // then
        assertThat(rrController.isClosed()).isTrue();
        assertThat(rrController.isResponseAvailable()).isFalse();
        assertThat(rrController.isFailed()).isFalse();
    }

    @Test
    public void shouldNotBeReusableIfNotClosed()
    {
        // given
        final RequestResponseController rrController = new RequestResponseController(transport);

        rrController.open(RECEIVER, new DirectBufferWriter().wrap(BUF1), null);

        // then
        exception.expect(IllegalStateException.class);
        exception.expectMessage("Cannot open state machine, has not been closed.");

        // when
        rrController.open(RECEIVER, new DirectBufferWriter().wrap(BUF1), null);
    }


}
