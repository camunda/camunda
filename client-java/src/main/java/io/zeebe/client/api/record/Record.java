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

import io.zeebe.client.ZeebeClient;

/**
 * A (generic) record in a partition. A record can be an event, a command or a
 * command rejection.
 */
public interface Record
{
    /**
     * @return the record's metadata, such as the topic and partition it belongs
     *         to
     */
    RecordMetadata getMetadata();

    /**
     * @return the record encoded as JSON. Use {@link ZeebeObjectMapper}
     *         accessible via {@link ZeebeClient#objectMapper()} for
     *         deserialization.
     */
    String toJson();

    /**
     * @return the key of the record. Multiple records can have the same key if
     *         they belongs to the same logical entity. Keys are unique for the
     *         combination of topic, partition and record type.
     */
    long getKey();

    /**
     * @return the unique position of the source record. Records are
     *         ordered by position.
     */
    long getSourceRecordPosition();
}
