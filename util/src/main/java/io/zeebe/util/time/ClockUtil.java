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
package io.zeebe.util.time;

import java.time.Duration;
import java.time.Instant;

public class ClockUtil
{
    private static volatile long currentTime;
    private static volatile long currentOffset;

    static
    {
        reset();
    }

    public static void setCurrentTime(long currentTime)
    {
        ClockUtil.currentTime = currentTime;
    }

    public static void setCurrentTime(Instant currentTime)
    {
        ClockUtil.currentTime = currentTime.toEpochMilli();
    }

    public static void pinCurrentTime()
    {
        setCurrentTime(getCurrentTime());
    }

    public static void addTime(Duration durationToAdd)
    {
        if (usesPointInTime())
        {
            currentTime += durationToAdd.toMillis();
        }
        else
        {
            currentOffset += durationToAdd.toMillis();
        }
    }

    public static void reset()
    {
        ClockUtil.currentTime = -1;
        ClockUtil.currentOffset = 0;
    }

    public static long getCurrentTimeInMillis()
    {
        if (usesPointInTime())
        {
            return currentTime;
        }
        else
        {
            long now = System.currentTimeMillis();
            if (usesOffset())
            {
                now = now + currentOffset;
            }
            return now;
        }
    }

    public static Instant getCurrentTime()
    {
        return Instant.ofEpochMilli(getCurrentTimeInMillis());
    }

    protected static boolean usesPointInTime()
    {
        return currentTime > 0;
    }

    protected static boolean usesOffset()
    {
        return currentOffset > 0;
    }

}
