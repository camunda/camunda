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
        assertThat(new ByteValue(1_000_000_000, BYTES).toBytes()).isEqualTo(new ByteValue(1_000_000_000, BYTES));
        assertThat(new ByteValue(1_000_000_000, BYTES).toKilobytes()).isEqualTo(new ByteValue(1_000_000, KILOBYTES));
        assertThat(new ByteValue(1_000_000_000, BYTES).toMegabytes()).isEqualTo(new ByteValue(1_000, MEGABYTES));
        assertThat(new ByteValue(1_000_000_000, BYTES).toGigabytes()).isEqualTo(new ByteValue(1, GIGABYTES));
    }

    @Test
    public void shouldConvertUnitKilobytes()
    {
        assertThat(new ByteValue(1_000_000, KILOBYTES).toBytes()).isEqualTo(new ByteValue(1_000_000_000, BYTES));
        assertThat(new ByteValue(1_000_000, KILOBYTES).toKilobytes()).isEqualTo(new ByteValue(1_000_000, KILOBYTES));
        assertThat(new ByteValue(1_000_000, KILOBYTES).toMegabytes()).isEqualTo(new ByteValue(1_000, MEGABYTES));
        assertThat(new ByteValue(1_000_000, KILOBYTES).toGigabytes()).isEqualTo(new ByteValue(1, GIGABYTES));
    }

    @Test
    public void shouldConvertUnitMegabytes()
    {
        assertThat(new ByteValue(1_000, MEGABYTES).toBytes()).isEqualTo(new ByteValue(1_000_000_000, BYTES));
        assertThat(new ByteValue(1_000, MEGABYTES).toKilobytes()).isEqualTo(new ByteValue(1_000_000, KILOBYTES));
        assertThat(new ByteValue(1_000, MEGABYTES).toMegabytes()).isEqualTo(new ByteValue(1_000, MEGABYTES));
        assertThat(new ByteValue(1_000, MEGABYTES).toGigabytes()).isEqualTo(new ByteValue(1, GIGABYTES));
    }

    @Test
    public void shouldConvertUnitGigabytes()
    {
        assertThat(new ByteValue(1, GIGABYTES).toBytes()).isEqualTo(new ByteValue(1_000_000_000, BYTES));
        assertThat(new ByteValue(1, GIGABYTES).toKilobytes()).isEqualTo(new ByteValue(1_000_000, KILOBYTES));
        assertThat(new ByteValue(1, GIGABYTES).toMegabytes()).isEqualTo(new ByteValue(1_000, MEGABYTES));
        assertThat(new ByteValue(1, GIGABYTES).toGigabytes()).isEqualTo(new ByteValue(1, GIGABYTES));
    }

    @Test
    public void shouldParseToString()
    {
        final ByteValue value = new ByteValue(71, ByteUnit.KILOBYTES);

        assertThat(new ByteValue(value.toString())).isEqualTo(value);
    }
}
