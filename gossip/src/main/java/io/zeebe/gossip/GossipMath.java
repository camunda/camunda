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
package io.zeebe.gossip;

public class GossipMath
{

    public static int gossipPeriodsToSpread(int repeatMult, int clusterSize)
    {
        return repeatMult * ceilLog2(clusterSize);
    }

    public static long suspicionTimeout(int suspicionMult, int clusterSize, long pingInterval)
    {
        return suspicionMult * ceilLog2(clusterSize) * pingInterval;
    }

    /**
     * Returns ceil(log2(n + 1)).
     */
    protected static int ceilLog2(int num)
    {
        return 32 - Integer.numberOfLeadingZeros(num);
    }

}
