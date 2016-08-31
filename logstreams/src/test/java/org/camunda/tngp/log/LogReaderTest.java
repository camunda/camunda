package org.camunda.tngp.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.camunda.tngp.util.buffer.BufferReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LogReaderTest
{

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mock
    protected Log log;

    @Mock
    protected BufferReader reader;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDecideHasNextBasedOnLastLogPosition()
    {
        // given
        when(log.getLastPosition()).thenReturn(123L);

        final LogReader logReader = new LogReaderImpl(log);
        logReader.setPosition(100L);

        // then
        assertThat(logReader.hasNext()).isTrue();

    }

    @Test
    public void shouldDecideHasNextBasedOnLastLogPositionCase2()
    {
        // given
        when(log.getLastPosition()).thenReturn(123L);

        final LogReader logReader = new LogReaderImpl(log);
        logReader.setPosition(200L);

        // then
        assertThat(logReader.hasNext()).isFalse();
    }

    @Test
    public void shouldNotReadIfNoNextEvent()
    {
        // given
        when(log.getLastPosition()).thenReturn(123L);

        final LogReader logReader = new LogReaderImpl(log);
        logReader.setPosition(200L);

        // then
        exception.expect(RuntimeException.class);

        // when
        logReader.read(reader);
    }
}
