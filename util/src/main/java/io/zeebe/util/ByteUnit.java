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

public enum ByteUnit
{
    BYTES
    {
        @Override
        public long toBytes(long b)
        {
            return b;
        }
        @Override
        public long toKilobytes(long d)
        {
            return d / 1_000;
        }
        @Override
        public long toMegabytes(long d)
        {
            return d / 1_000_000;
        }
        @Override
        public long toGigabytes(long d)
        {
            return d / 1_000_000_000;
        }
        @Override
        public String metric()
        {
            return BYTES_METRIC;
        }
    },

    KILOBYTES
    {
        @Override
        public long toBytes(long b)
        {
            return b * 1_000;
        }
        @Override
        public long toKilobytes(long d)
        {
            return d;
        }
        @Override
        public long toMegabytes(long d)
        {
            return d / 1_000;
        }
        @Override
        public long toGigabytes(long d)
        {
            return d / 1_000_000;
        }
        @Override
        public String metric()
        {
            return KILOBYTES_METRIC;
        }
    },

    MEGABYTES
    {
        @Override
        public long toBytes(long b)
        {
            return b * 1_000_000;
        }
        @Override
        public long toKilobytes(long d)
        {
            return d * 1_000;
        }
        @Override
        public long toMegabytes(long d)
        {
            return d;
        }
        @Override
        public long toGigabytes(long d)
        {
            return d / 1_000;
        }
        @Override
        public String metric()
        {
            return MEGABYTES_METRIC;
        }
    },

    GIGABYTES
    {
        @Override
        public long toBytes(long b)
        {
            return b * 1_000_000_000;
        }
        @Override
        public long toKilobytes(long d)
        {
            return d * 1_000_000;
        }
        @Override
        public long toMegabytes(long d)
        {
            return d * 1_000;
        }
        @Override
        public long toGigabytes(long d)
        {
            return d / 1;
        }
        @Override
        public String metric()
        {
            return GIGABYTES_METRIC;
        }
    };

    public static final String BYTES_METRIC = "";
    public static final String KILOBYTES_METRIC = "K";
    public static final String MEGABYTES_METRIC = "M";
    public static final String GIGABYTES_METRIC = "G";

    public long toBytes(long u)
    {
        throw new AbstractMethodError();
    }

    public long toKilobytes(long u)
    {
        throw new AbstractMethodError();
    }

    public long toMegabytes(long u)
    {
        throw new AbstractMethodError();
    }

    public long toGigabytes(long u)
    {
        throw new AbstractMethodError();
    }

    public String metric()
    {
        throw new AbstractMethodError();
    }
}
