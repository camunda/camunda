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
package org.camunda.tngp.client.event;

import static org.mockito.Mockito.verify;

import org.camunda.tngp.client.event.impl.cmd.PollEventsCmdImpl;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.protocol.event.PollEventsRequestWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PollEventsCmdTest
{
    @Mock
    protected PollEventsRequestWriter requestWriter;

    @Mock
    protected ClientCmdExecutor commandExecutor;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldSetProperties()
    {
        // given
        final PollEventsCmdImpl command = new PollEventsCmdImpl(commandExecutor);
        command.setRequestWriter(requestWriter);

        // when
        command
            .startPosition(5)
            .maxEvents(10)
            .topicId(1);

        // then
        verify(requestWriter).startPosition(5);
        verify(requestWriter).maxEvents(10);
        verify(requestWriter).topicId(1);
    }

}
