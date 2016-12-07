/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.logstreams.processor;

import java.util.Objects;

import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class StreamProcessorBuilder
{
    protected long id;
    protected String name;

    protected StreamProcessor streamProcessor;

    protected LogStream sourceStream;
    protected LogStream targetStream;

    protected AgentRunnerService agentRunnerService;

    public StreamProcessorBuilder(long id, String name, StreamProcessor streamProcessor)
    {
        this.id = id;
        this.name = name;
        this.streamProcessor = streamProcessor;
    }

    public StreamProcessorBuilder sourceStream(LogStream stream)
    {
        this.sourceStream = stream;
        return this;
    }

    public StreamProcessorBuilder targetStream(LogStream stream)
    {
        this.targetStream = stream;
        return this;
    }

    public StreamProcessorBuilder agentRunnerService(AgentRunnerService agentRunnerService)
    {
        this.agentRunnerService = agentRunnerService;
        return this;
    }

    public StreamProcessorController build()
    {
        Objects.requireNonNull(streamProcessor, "No stream processor provided.");
        Objects.requireNonNull(sourceStream, "No source stream provided.");
        Objects.requireNonNull(targetStream, "No target stream provided.");
        Objects.requireNonNull(agentRunnerService, "No agent runner service provided.");


        final StreamProcessorContext ctx = new StreamProcessorContext();

        ctx.setId(id);
        ctx.setName(name);

        ctx.setStreamProcessor(streamProcessor);

        ctx.setSourceStream(sourceStream);
        ctx.setTargetStream(targetStream);

        ctx.setAgentRunnerService(agentRunnerService);

        final LogStreamReader logStreamReader = new BufferedLogStreamReader();
        ctx.setLogStreamReader(logStreamReader);

        final LogStreamWriter logStreamWriter = new LogStreamWriter();
        ctx.setLogStreamWriter(logStreamWriter);

        return new StreamProcessorController(ctx);
    }

}
