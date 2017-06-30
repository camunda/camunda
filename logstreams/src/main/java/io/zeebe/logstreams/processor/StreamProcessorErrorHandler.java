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
    int RESULT_SUCCESS = 1;
    int RESULT_FAILURE = -1;
    int RESULT_REJECT = 0;

    /**
     * Handle the event processing error.
     *
     * @param failedEvent
     *            the event which causes the error
     * @param error
     *            the occurred error
     *
     * @return the result, should be one of {@link #RESULT_SUCCESS},
     *         {@link #RESULT_REJECT}, {@link #RESULT_FAILURE}
     */
    int onError(LoggedEvent failedEvent, Exception error);
}
