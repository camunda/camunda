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
package io.zeebe.transport;

import static org.assertj.core.api.Assertions.fail;

import org.junit.Ignore;
import org.junit.Test;

@Ignore("https://github.com/camunda-tngp/zb-transport/issues/25")
public class Issue25Test
{

    @Test
    public void failThis()
    {
        fail("implement these test cases");
        /*
         * - writing of transport message, request, response to send buffer and the error cases (e.g. when buffer is saturated)
         * - server-channels should always receive a new stream id
         * - error cases:
         *   - send buffer is saturated
         *   - request pool is exhausted
         *   - channel closes while requests are open
         *   - buffer writer throws exception
         */
    }
}
