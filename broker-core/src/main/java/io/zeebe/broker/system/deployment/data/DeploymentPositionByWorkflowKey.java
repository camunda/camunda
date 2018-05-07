/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.deployment.data;

import io.zeebe.map.Long2LongZbMap;

/**
 * workflow key -> deployment position
 *
 */
public class DeploymentPositionByWorkflowKey
{
    private final Long2LongZbMap map = new Long2LongZbMap();

    public long get(long key, long missingValue)
    {
        return map.get(key, missingValue);
    }

    public void set(long key, long value)
    {
        map.put(key, value);
    }

    public Long2LongZbMap getRawMap()
    {
        return map;
    }
}
