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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.protocol.clientapi.ControlMessageType;

public abstract class AbstractControlMessageWithoutResponseCmd<E> extends AbstractControlMessageCmd<E, Void>
{

    public AbstractControlMessageWithoutResponseCmd(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic, final Class<E> messageType, final ControlMessageType controlMessageType)
    {
        super(commandManager, objectMapper, topic, messageType, controlMessageType);
    }

    @Override
    protected Void getResponseValue(final E data)
    {
        return null;
    }

}
