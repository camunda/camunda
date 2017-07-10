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
package io.zeebe.transport.impl;

import org.agrona.DirectBuffer;

import io.zeebe.dispatcher.FragmentHandler;

public class ClientSendFailureHandler implements FragmentHandler
{
    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseHeaderDescriptor requestResponseHeaderDescriptor = new RequestResponseHeaderDescriptor();

    protected final ClientRequestPool requestPool;

    public ClientSendFailureHandler(ClientRequestPool requestPool)
    {
        this.requestPool = requestPool;
    }

    @Override
    public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
    {
        final int protocolId = transportHeaderDescriptor.wrap(buffer, offset).protocolId();
        if (protocolId == TransportHeaderDescriptor.REQUEST_RESPONSE)
        {
            requestResponseHeaderDescriptor.wrap(buffer, offset + TransportHeaderDescriptor.HEADER_LENGTH);
            final long requestId = requestResponseHeaderDescriptor.requestId();

            final ClientRequestImpl pendingRequest = requestPool.getOpenRequestById(requestId);
            if (pendingRequest != null)
            {
                // TODO: noch nicht so toll; wenn, dann sollte der Sende rhier eine Nachricht übergeben können
                pendingRequest.fail(new RuntimeException("Could not send request"));
            }

        }

        return CONSUME_FRAGMENT_RESULT;
    }

}
