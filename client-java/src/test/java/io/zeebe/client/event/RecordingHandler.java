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
package io.zeebe.client.event;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.subscription.RecordHandler;

public class RecordingHandler implements RecordHandler
{

    protected List<Record> records = new ArrayList<>();

    @Override
    public void onRecord(Record record)
    {
        this.records.add(record);
    }

    public int numRecordedRecords()
    {
        return records.size();
    }

    public List<Record> getRecordedRecords()
    {
        return records;
    }

    public void reset()
    {
        this.records.clear();
    }
}
