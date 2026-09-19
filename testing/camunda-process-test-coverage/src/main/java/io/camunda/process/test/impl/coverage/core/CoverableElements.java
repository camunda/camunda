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
package io.camunda.process.test.impl.coverage.core;

import java.util.Collections;
import java.util.Set;

/**
 * The elements of a process model that a coverage can cover, told apart by what kind of element
 * they are.
 *
 * <p>An id names an element only within the model that declares it, so the same id can be a flow
 * node in one deployment of a process definition id and a sequence flow in another. What an
 * instance completed is therefore matched against the flow nodes and what it took against the
 * sequence flows: matching both against all elements would let one element of the model count both
 * as completed and as taken.
 */
public final class CoverableElements {

  private final Set<String> flowNodeIds;
  private final Set<String> sequenceFlowIds;

  CoverableElements(final Set<String> flowNodeIds, final Set<String> sequenceFlowIds) {
    this.flowNodeIds = Collections.unmodifiableSet(flowNodeIds);
    this.sequenceFlowIds = Collections.unmodifiableSet(sequenceFlowIds);
  }

  /** Returns the ids of the flow nodes of the executable process. */
  public Set<String> getFlowNodeIds() {
    return flowNodeIds;
  }

  /** Returns the ids of the sequence flows that leave a flow node of the executable process. */
  public Set<String> getSequenceFlowIds() {
    return sequenceFlowIds;
  }

  /** Returns the number of elements a coverage of this model can cover. */
  public int count() {
    return flowNodeIds.size() + sequenceFlowIds.size();
  }
}
