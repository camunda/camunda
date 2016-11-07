/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.util.time;

import java.time.Instant;

public class ClockUtil
{
    private static volatile long currentTime = -1;

    public static void setCurrentTime(long currentTime)
    {
        ClockUtil.currentTime = currentTime;
    }

    public static void setCurrentTime(Instant currentTime)
    {
        ClockUtil.currentTime = currentTime.toEpochMilli();
    }

    public static void reset()
    {
        ClockUtil.currentTime = -1;
    }

    public static long getCurrentTimeInMillis()
    {
        if (currentTime > 0)
        {
            return currentTime;
        }
        return System.currentTimeMillis();
    }

    public static Instant getCurrentTime()
    {
        if (currentTime > 0)
        {
            return Instant.ofEpochMilli(currentTime);
        }
        return Instant.now();
    }

}
