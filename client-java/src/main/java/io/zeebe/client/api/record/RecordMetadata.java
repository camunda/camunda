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
package io.zeebe.client.api.record;

import java.time.Instant;

/**
 * The metadata of a record.
 */
public interface RecordMetadata
{
    /**
     * @return the name of the topic this record is published on
     */
    String getTopicName();

    /**
     * @return the id of the partition this record is published on
     */
    int getPartitionId();

    /**
     * @return the unique position the record has in the partition. Records are
     *         ordered by position.
     */
    long getPosition();

    /**
     * @return the key of the record. Multiple records can have the same key if
     *         they belongs to the same logical entity. Keys are unique for the
     *         combination of topic, partition and record type.
     */
    long getKey();

    /**
     * @return the timestamp of the record. This is the time at which the event
     *         was written to the log.
     */
    Instant getTimestamp();

    /**
     * @return the type of the record (event, command or command rejection)
     */
    RecordType getRecordType();

    /**
     * @return the type of the record (e.g. job, worklow, workflow instance,
     *         etc.)
     */
    ValueType getValueType();

    /**
     * @return either the event or the command name, depending if the record is
     *         an event or a command (rejection)
     */
    String getIntent();

    /**
     * @return the type of rejection if {@link #getRecordType()} returns {@link RecordType#COMMAND_REJECTION}
     * or else <code>null</code>.
     */
    RejectionType getRejectionType();

    /**
     * @return the reason why a command was rejected if {@link #getRecordType()} returns {@link RecordType#COMMAND_REJECTION}
     * or else <code>null</code>.
     */
    String getRejectionReason();
}
