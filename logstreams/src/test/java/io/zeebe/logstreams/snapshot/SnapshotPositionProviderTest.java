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
