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
package io.zeebe.client.impl.topic;

import java.util.Collections;

import io.zeebe.client.api.commands.Topics;
import io.zeebe.client.api.commands.TopicsRequestStep1;
import io.zeebe.client.impl.ControlMessageRequest;
import io.zeebe.client.impl.RequestManager;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class TopicsRequestImpl extends ControlMessageRequest<Topics> implements TopicsRequestStep1
{

    public TopicsRequestImpl(RequestManager client)
    {
        super(client, ControlMessageType.REQUEST_PARTITIONS, TopicsImpl.class);

        setTargetPartition(Protocol.SYSTEM_PARTITION);
    }

    @Override
    public Object getRequest()
    {
        return Collections.EMPTY_MAP;
    }

}
