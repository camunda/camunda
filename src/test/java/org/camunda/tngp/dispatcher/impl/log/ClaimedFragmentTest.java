package org.camunda.tngp.dispatcher.impl.log;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.junit.Before;
import org.junit.Test;

import org.agrona.concurrent.UnsafeBuffer;

public class ClaimedFragmentTest
{

    private static final int A_FRAGMENT_LENGTH = 1024;
    UnsafeBuffer underlyingBuffer;
    ClaimedFragment claimedFragment;

    @Before
    public void stetup()
    {
        underlyingBuffer = new UnsafeBuffer(new byte[A_FRAGMENT_LENGTH]);
        claimedFragment = new ClaimedFragment();
    }

    @Test
    public void shouldCommit()
    {
        // given
        claimedFragment.wrap(underlyingBuffer, 0, A_FRAGMENT_LENGTH);

        // if
        claimedFragment.commit();

        // then
        assertThat(underlyingBuffer.getInt(lengthOffset(0))).isEqualTo(A_FRAGMENT_LENGTH - HEADER_LENGTH);
        assertThat(claimedFragment.getOffset()).isEqualTo(HEADER_LENGTH);
        assertThat(claimedFragment.getLength()).isEqualTo(-HEADER_LENGTH);
    }

    @Test
    public void shouldReturnOffsetAndLength()
    {
        // if
        claimedFragment.wrap(underlyingBuffer, 0, A_FRAGMENT_LENGTH);

        // then
        assertThat(claimedFragment.getOffset()).isEqualTo(HEADER_LENGTH);
        assertThat(claimedFragment.getLength()).isEqualTo(A_FRAGMENT_LENGTH - HEADER_LENGTH);
    }

}
