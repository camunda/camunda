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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.ClusterBehaviorIntent;
import org.immutables.value.Value;

/**
 * The behavior version commit.
 *
 * <p>This is a marker for now: the value type, its intents and the record exist so that their ids
 * are reserved. Its properties are defined in #68930.
 *
 * <p>See {@link ClusterBehaviorIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableClusterBehaviorRecordValue.Builder.class)
public interface ClusterBehaviorRecordValue extends RecordValue {}
