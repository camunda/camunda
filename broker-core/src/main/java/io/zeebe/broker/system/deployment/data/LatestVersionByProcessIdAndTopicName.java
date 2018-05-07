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

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.map.Bytes2LongZbMap;
import io.zeebe.model.bpmn.impl.ZeebeConstraints;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * (bpmn-process-id, topicName) -> version
 */
public class LatestVersionByProcessIdAndTopicName
{
    private static final int BPMN_PROCESS_ID_LENGTH = ZeebeConstraints.ID_MAX_LENGTH * SIZE_OF_CHAR;
    private static final int KEY_LENGTH = BPMN_PROCESS_ID_LENGTH + LogStream.MAX_TOPIC_NAME_LENGTH;

    private static final int BPMN_PROCESS_ID_OFFSET = 0;
    private static final int TOPIC_NAME_OFFSET = BPMN_PROCESS_ID_LENGTH;

    private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[KEY_LENGTH]);

    private final Bytes2LongZbMap map = new Bytes2LongZbMap(KEY_LENGTH);

    public Bytes2LongZbMap getRawMap()
    {
        return map;
    }

    public int getLatestVersion(DirectBuffer topicName, DirectBuffer bpmnProcessId, int missingValue)
    {
        wrap(topicName, bpmnProcessId);

        return (int) map.get(buffer, 0, buffer.capacity(), missingValue);
    }

    public void setLatestVersion(DirectBuffer topicName, DirectBuffer bpmnProcessId, int version)
    {
        wrap(topicName, bpmnProcessId);

        map.put(buffer, 0, buffer.capacity(), version);
    }

    private void wrap(DirectBuffer topicName, DirectBuffer bpmnProcessId)
    {
        buffer.setMemory(0, KEY_LENGTH, (byte) 0);

        buffer.putBytes(BPMN_PROCESS_ID_OFFSET, bpmnProcessId, 0, bpmnProcessId.capacity());
        buffer.putBytes(TOPIC_NAME_OFFSET, topicName, 0, topicName.capacity());
    }
}
