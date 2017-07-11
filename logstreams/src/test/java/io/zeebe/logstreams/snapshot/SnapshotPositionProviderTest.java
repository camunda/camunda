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
package io.zeebe.logstreams.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.LastProcessedEventPositionProvider;
import io.zeebe.logstreams.processor.LastWrittenEventPositionProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SnapshotPositionProviderTest
{
    private static final long EVENT_POSITION = 3L;

    @Mock
    protected LoggedEvent event;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        when(event.getPosition()).thenReturn(EVENT_POSITION);
    }

    @Test
    public void testLastProcessedEventPositionProvider()
    {
        // given
        final LastProcessedEventPositionProvider positionProvider = new LastProcessedEventPositionProvider();

        // when
        final long snapshotPosition = positionProvider.getSnapshotPosition(event, 52L);

        // then
        assertThat(snapshotPosition).isEqualTo(EVENT_POSITION);
    }

    @Test
    public void testLastWrittenEventPositionProvider()
    {
        // given
        final LastWrittenEventPositionProvider positionProvider = new LastWrittenEventPositionProvider();

        // when
        final long snapshotPosition = positionProvider.getSnapshotPosition(event, 52L);

        // then
        assertThat(snapshotPosition).isEqualTo(52L);
    }
}
