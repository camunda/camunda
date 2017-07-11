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
package io.zeebe.logstreams.processor;

import io.zeebe.logstreams.log.LoggedEvent;

/**
 * Handle errors which occur while processing events from logstream. An
 * implementation may create incidents or send an error responses. If an error
 * can not be handled then the stream processor stops processing further events.
 * If the handler fails to handle an error then the handler is invoked again.
 */
public interface StreamProcessorErrorHandler
{

    /**
     * Check if the handler can this error.
     *
     * @param error
     *            the occurred error
     * @return <code>true</code>, if the error can be handled.
     */
    boolean canHandle(Exception error);

    /**
     * Handle the event processing error.
     *
     * @param failedEvent
     *            the event which causes the error
     * @param error
     *            the occurred error
     *
     * @return <code>true</code>, if the event is handled successfully.
     */
    boolean onError(LoggedEvent failedEvent, Exception error);
}
