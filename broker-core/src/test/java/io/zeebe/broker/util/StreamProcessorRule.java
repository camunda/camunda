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
package io.zeebe.broker.util;

import java.util.function.Function;

import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.transport.clientapi.BufferingServerOutput;
import io.zeebe.broker.util.TestStreams.FluentLogWriter;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;

public class StreamProcessorRule implements TestRule
{
    // environment
    private TemporaryFolder tempFolder = new TemporaryFolder();
    private AutoCloseableRule closeables = new AutoCloseableRule();
    private ControlledActorClock clock = new ControlledActorClock();
    private ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);

    // things provisioned by this rule
    public static final String STREAM_NAME = "stream";

    private BufferingServerOutput output;
    private TestStreams streams;
    private TypedStreamEnvironment streamEnvironment;

    private SetupRule rule = new SetupRule();

    private RuleChain chain = RuleChain
            .outerRule(tempFolder)
            .around(actorSchedulerRule)
            .around(closeables)
            .around(rule);

    @Override
    public Statement apply(Statement base, Description description)
    {
        return chain.apply(base, description);
    }

    public StreamProcessorControl runStreamProcessor(Function<TypedStreamEnvironment, StreamProcessor> factory)
    {
        final StreamProcessorControl control = initStreamProcessor(factory);
        control.start();
        return control;
    }

    public StreamProcessorControl initStreamProcessor(Function<TypedStreamEnvironment, StreamProcessor> factory)
    {
        return streams.initStreamProcessor(STREAM_NAME, 0, () -> factory.apply(streamEnvironment));
    }

    public ControlledActorClock getClock()
    {
        return clock;
    }

    public TypedEventStream events()
    {
        return new TypedEventStream(streams.events(STREAM_NAME));
    }

    public long writeEvent(long key, UnpackedObject value)
    {
        return streams.newEvent(STREAM_NAME)
            .key(key)
            .event(value)
            .write();
    }

    public FluentLogWriter newEvent()
    {
        return streams.newEvent(STREAM_NAME);
    }

    public long writeEvent(UnpackedObject value)
    {
        return streams.newEvent(STREAM_NAME)
            .event(value)
            .write();
    }

    public void truncateLog(long position)
    {
        streams.truncate(STREAM_NAME, position);
    }

    public BufferingServerOutput getOutput()
    {
        return output;
    }

    private class SetupRule extends ExternalResource
    {

        @Override
        protected void before() throws Throwable
        {
            output = new BufferingServerOutput();

            streams = new TestStreams(tempFolder.getRoot(), closeables, actorSchedulerRule.get());
            streams.createLogStream(STREAM_NAME);

            streams.newEvent(STREAM_NAME) // TODO: workaround for https://github.com/zeebe-io/zeebe/issues/478
                .event(new UnpackedObject())
                .write();

            streamEnvironment = new TypedStreamEnvironment(streams.getLogStream(STREAM_NAME), output);
        }
    }

}
