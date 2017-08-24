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
package io.zeebe.protocol;

import java.nio.ByteOrder;

import org.agrona.DirectBuffer;

import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.util.buffer.BufferUtil;

public class Protocol
{

    public static final int PROTOCOL_VERSION = ExecuteCommandRequestDecoder.SCHEMA_VERSION;

    /**
     * The endianness of multibyte values encoded in the protocol. This MUST match the
     * default byte order in the SBE XML schema.
     */
    public static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;

    /**
     * The null value of an instant property which indicates that it is not set.
     */
    public static final long INSTANT_NULL_VALUE = Long.MIN_VALUE;

    /**
     * By convention, the name of the topic that can be used for topic creation commands
     */
    public static final String SYSTEM_TOPIC = ".system";
    public static final DirectBuffer SYSTEM_TOPIC_BUF = BufferUtil.wrapString(SYSTEM_TOPIC);

    /**
     * By convention, the partition id that can be used for topic creation commands
     */
    public static final int SYSTEM_PARTITION = 0;

}
