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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.immutables.value.Value;

/**
 * Represents metadata for a user-triggered request that may complete asynchronously.
 *
 * <p>This record is used to persist essential metadata about the original command. It allows
 * follow-up events or responses to be written after the request completes — even if its completion
 * was deferred due to intermediate operations like user task listener execution.
 *
 * <p>See {@link io.camunda.zeebe.protocol.record.intent.AsyncRequestMetadataIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableAsyncRequestMetadataRecordValue.Builder.class)
public interface AsyncRequestMetadataRecordValue extends RecordValue {

  /** The key of the original request command. */
  long getRequestKey();

  /** The value type of the original request. */
  ValueType getValueType();

  /** The intent of the original request. */
  Intent getIntent();

  /** The request ID from the client that issued the command. */
  long getRequestId();

  /** The request stream ID from the client that issued the command. */
  int getRequestStreamId();

  /** The operation reference associated with the original command, for traceability. */
  long getOperationReference();
}
