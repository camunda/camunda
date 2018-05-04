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
package io.zeebe.util;

import static io.zeebe.util.ByteUnit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class ByteValueTest
{
    @Test
    public void shouldParseValidStringValues()
    {
        assertThat(new ByteValue("10").getUnit()).isEqualTo(BYTES);
        assertThat(new ByteValue("10").getValue()).isEqualTo(10);

        assertThat(new ByteValue("11K").getUnit()).isEqualTo(KILOBYTES);
        assertThat(new ByteValue("11").getValue()).isEqualTo(11);

        assertThat(new ByteValue("12M").getUnit()).isEqualTo(MEGABYTES);
        assertThat(new ByteValue("12").getValue()).isEqualTo(12);

        assertThat(new ByteValue("13G").getUnit()).isEqualTo(GIGABYTES);
        assertThat(new ByteValue("13").getValue()).isEqualTo(13);
    }

    @Test
    public void shouldParseValidStringValuesCaseInsensitive()
    {
        assertThat(new ByteValue("11k").getUnit()).isEqualTo(KILOBYTES);
        assertThat(new ByteValue("12m").getUnit()).isEqualTo(MEGABYTES);
        assertThat(new ByteValue("13g").getUnit()).isEqualTo(GIGABYTES);
    }

    @Test
    public void shouldThrowOnInvalidUnit()
    {
        assertThatThrownBy(() -> new ByteValue("99f"))
            .isExactlyInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("Illegal byte value");
    }

    @Test
    public void shouldConvertUnitBytes()
    {
        final long byteValue = 1_000_000_000L;
        assertThat(new ByteValue(byteValue, BYTES).toBytes()).isEqualTo(new ByteValue(byteValue, BYTES));
        assertThat(new ByteValue(byteValue, BYTES).toKilobytes()).isEqualTo(new ByteValue(byteValue / 1024, KILOBYTES));
        assertThat(new ByteValue(byteValue, BYTES).toMegabytes()).isEqualTo(new ByteValue(byteValue / (1024 * 1024), MEGABYTES));
        assertThat(new ByteValue(byteValue, BYTES).toGigabytes()).isEqualTo(new ByteValue(byteValue / (1024 * 1024 * 1024), GIGABYTES));
    }

    @Test
    public void shouldConvertUnitKilobytes()
    {
        final long kiloByteValue = 1_000_000L;
        assertThat(new ByteValue(kiloByteValue, KILOBYTES).toBytes()).isEqualTo(new ByteValue(kiloByteValue * 1024, BYTES));
        assertThat(new ByteValue(kiloByteValue, KILOBYTES).toKilobytes()).isEqualTo(new ByteValue(kiloByteValue, KILOBYTES));
        assertThat(new ByteValue(kiloByteValue, KILOBYTES).toMegabytes()).isEqualTo(new ByteValue(kiloByteValue / 1024, MEGABYTES));
        assertThat(new ByteValue(kiloByteValue, KILOBYTES).toGigabytes()).isEqualTo(new ByteValue(kiloByteValue / (1024 * 1024), GIGABYTES));
    }

    @Test
    public void shouldConvertUnitMegabytes()
    {
        final long megaByteValue = 1_000L;
        assertThat(new ByteValue(megaByteValue, MEGABYTES).toBytes()).isEqualTo(new ByteValue(megaByteValue * (1024 * 1024), BYTES));
        assertThat(new ByteValue(megaByteValue, MEGABYTES).toKilobytes()).isEqualTo(new ByteValue(megaByteValue * 1024, KILOBYTES));
        assertThat(new ByteValue(megaByteValue, MEGABYTES).toMegabytes()).isEqualTo(new ByteValue(megaByteValue, MEGABYTES));
        assertThat(new ByteValue(megaByteValue, MEGABYTES).toGigabytes()).isEqualTo(new ByteValue(megaByteValue / 1024, GIGABYTES));
    }

    @Test
    public void shouldConvertUnitGigabytes()
    {
        final long gigaBytes = 100L;
        assertThat(new ByteValue(gigaBytes, GIGABYTES).toBytes()).isEqualTo(new ByteValue(gigaBytes * (1024 * 1024 * 1024), BYTES));
        assertThat(new ByteValue(gigaBytes, GIGABYTES).toKilobytes()).isEqualTo(new ByteValue(gigaBytes * (1024 * 1024), KILOBYTES));
        assertThat(new ByteValue(gigaBytes, GIGABYTES).toMegabytes()).isEqualTo(new ByteValue(gigaBytes * 1024, MEGABYTES));
        assertThat(new ByteValue(gigaBytes, GIGABYTES).toGigabytes()).isEqualTo(new ByteValue(gigaBytes, GIGABYTES));
    }

    @Test
    public void shouldParseToString()
    {
        final ByteValue value = new ByteValue(71, ByteUnit.KILOBYTES);

        assertThat(new ByteValue(value.toString())).isEqualTo(value);
    }
}
