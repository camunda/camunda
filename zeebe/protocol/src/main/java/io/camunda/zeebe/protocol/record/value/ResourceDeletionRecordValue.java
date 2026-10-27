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
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import org.immutables.value.Value;

/**
 * Represents a resource deletion event
 *
 * <p>See {@link ResourceDeletionIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableResourceDeletionRecordValue.Builder.class)
public interface ResourceDeletionRecordValue
    extends RecordValue, TenantOwned, BatchOperationRelated {

  /**
   * @return the key of the resource that will be deleted
   */
  long getResourceKey();

  /**
   * @return whether resource history should be deleted
   */
  boolean isDeleteHistory();

  /**
   * @return the batch operation type
   */
  BatchOperationType getBatchOperationType();

  /**
   * @return the type of the deleted resource
   */
  ResourceType getResourceType();

  /**
   * @return the id of the deleted resource
   */
  String getResourceId();
}
