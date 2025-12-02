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

/**
 * Represents a cluster variable resolver record that evaluates a FEEL expression containing a
 * reference to a cluster variable. This is a read-only operation used by inbound connectors to
 * resolve variable references from raw BPMN properties.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableClusterVariableResolverRecordValue.Builder.class)
public interface ClusterVariableResolverRecordValue extends RecordValue, TenantOwned {

  /**
   * The reference string containing the FEEL expression to be resolved (e.g.,
   * "camunda.vars.tenant.MY_KEY")
   *
   * @return the reference string
   */
  String getReference();

  /**
   * The resolved value after evaluating the FEEL expression. This will be null if the resolution is
   * still pending or if there was an error.
   *
   * @return the resolved value as a String, or null
   */
  String getResolvedValue();
}
