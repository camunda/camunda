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
import org.immutables.value.Value;

/** A persisted continuation of depth-first traversal while a process instance is suspending. */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableSuspensionBatchRecordValue.Builder.class)
public interface SuspensionBatchRecordValue extends RecordValue, ProcessInstanceRelated {

  /**
   * @return the element key or inclusive child-key cursor to visit; -1 starts at the first child.
   *     For traversal completion, this identifies the scope whose subtree has been visited.
   */
  long getIndexKey();

  /**
   * @return the containing scope used to find children and siblings; -1 identifies the traversal
   *     root without a parent.
   */
  long getParentKey();
}
