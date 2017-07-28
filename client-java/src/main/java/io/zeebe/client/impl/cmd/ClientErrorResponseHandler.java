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
package io.zeebe.client.impl.cmd;

import org.agrona.DirectBuffer;
import io.zeebe.client.cmd.BrokerErrorException;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;

public class ClientErrorResponseHandler
{
    protected ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    public Throwable createException(final DirectBuffer responseBuffer, final int offset, final int blockLength, final int version)
    {
        errorResponseDecoder.wrap(responseBuffer, offset, blockLength, version);

        final ErrorCode errorCode = errorResponseDecoder.errorCode();
        final String errorData = errorResponseDecoder.errorData();

        return new BrokerErrorException(errorCode, errorData);
    }

}
