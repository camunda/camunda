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
package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractControlMessageWithoutResponseCmd<E> extends AbstractControlMessageCmd<E, Void>
{

    public AbstractControlMessageWithoutResponseCmd(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper, Class<E> messageType,
            ControlMessageType controlMessageType)
    {
        super(cmdExecutor, objectMapper, messageType, controlMessageType);
    }

    @Override
    protected Void getResponseValue(int channelId, E data)
    {
        return null;
    }

}
