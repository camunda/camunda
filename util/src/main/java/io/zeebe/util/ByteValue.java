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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ByteValue
{
    private static final Pattern PATTERN = Pattern.compile("(\\d+)([K|M|G]?)", Pattern.CASE_INSENSITIVE);

    private final ByteUnit unit;
    private final long value;

    public ByteValue(long value, ByteUnit unit)
    {
        this.value = value;
        this.unit = unit;
    }

    public ByteValue(String humanReadable)
    {
        final Matcher matcher = PATTERN.matcher(humanReadable);

        if (!matcher.matches())
        {
            final String err = String.format("Illegal byte value '%s'. Must match '%s' Valid examples: 100MB, 4Kb, ...", humanReadable, PATTERN.pattern());
            throw new IllegalArgumentException(err);
        }

        final String valueString = matcher.group(1);
        value = Long.parseLong(valueString);

        final String unitString = matcher.group(2);

        switch (unitString)
        {
            case KILOBYTES_METRIC:
                unit = ByteUnit.KILOBYTES;
                break;

            case MEGABYTES_METRIC:
                unit = ByteUnit.MEGABYTES;
                break;

            case GIGABYTES_METRIC:
                unit = ByteUnit.GIGABYTES;
                break;

            default:
                unit = ByteUnit.BYTES;
                break;
        }
    }

    public ByteUnit getUnit()
    {
        return unit;
    }

    public long getValue()
    {
        return value;
    }

    public ByteValue toBytes()
    {
        return new ByteValue(unit.toBytes(value), BYTES);
    }

    public ByteValue toKilobytes()
    {
        return new ByteValue(unit.toKilobytes(value), KILOBYTES);
    }

    public ByteValue toMegabytes()
    {
        return new ByteValue(unit.toMegabytes(value), MEGABYTES);
    }

    public ByteValue toGigabytes()
    {
        return new ByteValue(unit.toGigabytes(value), ByteUnit.GIGABYTES);
    }

    @Override
    public String toString()
    {
        return String.format("%d%s", value, unit.metric());
    }
}
