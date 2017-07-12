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
package io.zeebe.raft;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.transport.SocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Loggers
{

    public static Logger getRaftLogger(final SocketAddress socketAddress, final LogStream logStream)
    {
        final String loggerName = String.format("io.zeebe.raft - %s - %s:%d", logStream.getLogName(), socketAddress.host(), socketAddress.port());

        return LoggerFactory.getLogger(loggerName);
    }

}
