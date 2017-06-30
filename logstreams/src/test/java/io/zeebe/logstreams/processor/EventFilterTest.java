package io.zeebe.logstreams.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.zeebe.logstreams.log.LoggedEvent;

import org.junit.Test;

public class EventFilterTest
{

    @Test
    public void testConjunction()
    {
        // given
        final EventFilter acceptFilter = (e) -> true;
        final EventFilter rejectFilter = (e) -> false;

        final LoggedEvent event = mock(LoggedEvent.class);

        // when/then
        assertThat(acceptFilter.and(acceptFilter).applies(event)).isTrue();
        assertThat(acceptFilter.and(rejectFilter).applies(event)).isFalse();
        assertThat(rejectFilter.and(rejectFilter).applies(event)).isFalse();
    }
}
