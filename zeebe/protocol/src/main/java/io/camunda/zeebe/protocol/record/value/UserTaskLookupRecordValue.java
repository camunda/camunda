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
import java.util.List;
import org.immutables.value.Value;

/**
 * A read of an active user task and its variables from the engine state.
 *
 * <p>The user task to read is the record's key. Every read is written to the log, so the record on
 * the log only identifies what to read; the user task and its variables are only set on the
 * command's response.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableUserTaskLookupRecordValue.Builder.class)
public interface UserTaskLookupRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the names of the variables to read; empty to read all variables visible to the user
   *     task
   */
  List<String> getFetchVariables();

  /**
   * Only set on the command's response; empty on the record on the log.
   *
   * @return the user task as stored in the engine, with the variables visible to it (filtered by
   *     {@link #getFetchVariables()}) as its variables
   */
  UserTaskRecordValue getUserTask();

  /**
   * Only set on the command's response; {@link UserTaskLifecycleState#UNSPECIFIED} on the record on
   * the log.
   *
   * @return the user task's lifecycle state in the engine; while a change is in progress, {@link
   *     #getUserTask()} still holds the values from before it
   */
  UserTaskLifecycleState getLifecycleState();
}
