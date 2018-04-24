/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.event.handler;

import io.zeebe.broker.event.processor.CloseSubscriptionRequest;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.transport.clientapi.BufferingServerOutput;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@Ignore
public class RemoveTopicSubscriptionHandlerTest
{

    protected FuturePool futurePool;

    @Mock
    protected TopicSubscriptionService subscriptionService;

    @Rule
    public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

    protected BufferingServerOutput output;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        output = new BufferingServerOutput();

        futurePool = new FuturePool();
        when(subscriptionService.closeSubscriptionAsync(anyInt(), anyLong())).thenAnswer((invocation) -> futurePool.next());
    }

    @Test
    public void shouldWriteErrorOnFailure()
    {
        // given
        final RemoveTopicSubscriptionHandler handler = new RemoveTopicSubscriptionHandler(output, subscriptionService);

        final RecordMetadata metadata = new RecordMetadata();
        metadata.requestStreamId(14);

        final DirectBuffer request = encode(new CloseSubscriptionRequest().setSubscriberKey(5L));
        actorSchedulerRule.submitActor(new Handler((actor) -> handler.handle(actor, 0, request, metadata)));
        actorSchedulerRule.workUntilDone();

        // when
        futurePool.at(0).completeExceptionally(new RuntimeException("foo"));
        actorSchedulerRule.workUntilDone();

        // then
        assertThat(output.getSentResponses()).hasSize(1);

        final ErrorResponseDecoder errorDecoder = output.getAsErrorResponse(0);

        assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorDecoder.errorData()).isEqualTo("Cannot close topic subscription. foo");
    }

    protected static final DirectBuffer encode(UnpackedObject obj)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[obj.getLength()]);
        obj.write(buffer, 0);
        return buffer;
    }

    class Handler extends Actor
    {

        private final Consumer<ActorControl> handler;

        Handler(final Consumer<ActorControl> handler)
        {

            this.handler = handler;
        }


        @Override
        protected void onActorStarted()
        {
            handler.accept(actor);
        }
    }
}
