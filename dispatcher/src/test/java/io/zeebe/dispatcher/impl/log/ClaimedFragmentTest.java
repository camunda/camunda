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
package io.zeebe.dispatcher.impl.log;

import static org.assertj.core.api.Assertions.*;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.*;

import io.zeebe.dispatcher.ClaimedFragment;
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
        assertThat(underlyingBuffer.getInt(lengthOffset(0))).isEqualTo(A_FRAGMENT_LENGTH);
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
