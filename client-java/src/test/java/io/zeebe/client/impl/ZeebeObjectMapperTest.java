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
package io.zeebe.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.zeebe.client.event.impl.TaskEventImpl;
import org.junit.Test;

public class ZeebeObjectMapperTest
{
    private final ZeebeObjectMapper objectMapper = new ZeebeObjectMapper();

    @Test
    public void convertTaskEntityFromToJson() throws IOException
    {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("activityId", "task_doSomething");
        headers.put("workflowKey", 4294975304L);
        headers.put("workflowInstanceKey", 4294975520L);
        headers.put("bpmnProcessId", "process_dummy");
        headers.put("activityInstanceKey", 4294976512L);
        headers.put("workflowDefinitionVersion", 1);

        final Map<String, Object> customHeaders = new HashMap<>();
        customHeaders.put("some", "value");

        final TaskEventImpl task = new TaskEventImpl("CREATED", objectMapper.getMsgPackConverter());
        task.setTopicName("topic");
        task.setPartitionId(1);
        task.setEventPosition(10L);
        task.setKey(20L);
        task.setType("type");
        task.setRetries(3);
        task.setPayload("{\"foo\":\"bar\"}");
        task.setLockTime(1000L);
        task.setHeaders(headers);
        task.setLockOwner("owner");
        task.setCustomHeaders(customHeaders);

        final byte[] json = objectMapper.writeValueAsBytes(task);

        // since equals/hashcode is not implemented on taskEvent, use the comparision of toString
        assertThat(objectMapper.readValue(json, TaskEventImpl.class).toString()).isEqualTo(task.toString());
    }

    @Test
    public void writeValueAsStringFails()
    {
        assertThatThrownBy(() -> objectMapper.writeValueAsString("some data")).isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("does not support 'writeValueAsString'. Use 'writeValueAsBytes'");
    }
}
