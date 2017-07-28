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
package io.zeebe.client.cmd;

import java.util.concurrent.Future;

import io.zeebe.client.impl.Partition;

public interface ClientCommand<R>
{
    /**
     * Executes the command and blocks until the result is available.
     * Throws {@link RuntimeException} in case the command times out.
     *
     * @return the result of the command.
     */
    R execute();

    /**
     * Executes the command asynchronously and returns control to the client thread.
     *
     * @return a future of the command result
     */
    Future<R> executeAsync();

    /**
     * The topic of the command. Returns {@code null} if the command is not related to a topic.
     *
     * @return the topic related to this command, or null
     */
    Partition getTopic();

}
