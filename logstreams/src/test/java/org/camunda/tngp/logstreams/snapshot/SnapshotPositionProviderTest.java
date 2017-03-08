package org.camunda.tngp.logstreams.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.LastProcessedEventPositionProvider;
import org.camunda.tngp.logstreams.processor.LastWrittenEventPositionProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SnapshotPositionProviderTest
{

    public static final long EVENT_POSITION = 3L;

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
