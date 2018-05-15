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
package io.zeebe.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.subscription.RecordHandler;

public class RecordingEventHandler implements RecordHandler
{

    protected List<Record> records = new CopyOnWriteArrayList<>();
    protected ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public void onRecord(Record event)
    {
        this.records.add(event);
    }

    public int numRecords()
    {
        return records.size();
    }

    public int numRecordsOfType(RecordMetadata.ValueType type)
    {
        return (int) records.stream().filter(e -> e.getMetadata().getValueType() == type).count();
    }

    public int numJobRecords()
    {
        return numRecordsOfType(RecordMetadata.ValueType.JOB);
    }

    public int numRaftRecords()
    {
        return numRecordsOfType(RecordMetadata.ValueType.RAFT);
    }

    public List<Record> getRecords()
    {
        return records;
    }

    public void assertJobRecord(int index, long taskKey, String intent) throws IOException
    {
        final List<Record> taskEvents = records.stream()
                .filter(e -> e.getMetadata().getValueType() == RecordMetadata.ValueType.JOB)
                .collect(Collectors.toList());

        final Record taskEvent = taskEvents.get(index);

        final RecordMetadata eventMetadata = taskEvent.getMetadata();
        assertThat(eventMetadata.getValueType()).isEqualTo(RecordMetadata.ValueType.JOB);
        assertThat(eventMetadata.getKey()).isEqualTo(taskKey);
        assertThat(eventMetadata.getIntent()).isEqualTo(intent);
    }

    public void reset()
    {
        this.records.clear();
    }
}
