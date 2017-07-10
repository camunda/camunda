/**
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
package io.zeebe.client.impl.cmd;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import io.zeebe.client.cmd.ClientCommand;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.util.buffer.RequestWriter;

public abstract class AbstractCmdImpl<R> implements ClientCommand<R>
{
    protected static final Charset CHARSET = StandardCharsets.UTF_8;

    private final ClientCommandManager commandManager;
    protected final Topic topic;

    public AbstractCmdImpl(final ClientCommandManager commandManager, final Topic topic)
    {
        this.commandManager = commandManager;
        this.topic = topic;
    }

    @Override
    public R execute()
    {
        return commandManager.execute(this);
    }

    @Override
    public Future<R> executeAsync()
    {
        return commandManager.executeAsync(this);
    }

    @Override
    public Topic getTopic()
    {
        return topic;
    }

    public abstract ClientResponseHandler<R> getResponseHandler();

    public abstract RequestWriter getRequestWriter();

}
