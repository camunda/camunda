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

import static org.agrona.BitUtil.SIZE_OF_CHAR;

import io.zeebe.map.Bytes2LongZbMap;
import io.zeebe.model.bpmn.impl.ZeebeConstraints;
import org.agrona.DirectBuffer;

/**
 * bpmn-process-id -> latest-version
 */
public class WorkflowVersions
{
    private static final int VALUE_LENGTH = ZeebeConstraints.ID_MAX_LENGTH * SIZE_OF_CHAR;

    private final Bytes2LongZbMap map = new Bytes2LongZbMap(VALUE_LENGTH);

    public Bytes2LongZbMap getRawMap()
    {
        return map;
    }

    public int getLatestVersion(DirectBuffer bpmnProcessId, int missingValue)
    {
        return (int) map.get(bpmnProcessId, 0, bpmnProcessId.capacity(), missingValue);
    }

    public void setLatestVersion(DirectBuffer bpmnProcessId, int version)
    {
        map.put(bpmnProcessId, 0, bpmnProcessId.capacity(), version);
    }
}
