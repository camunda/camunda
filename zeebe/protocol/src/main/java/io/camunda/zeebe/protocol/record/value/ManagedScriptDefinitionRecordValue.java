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

@Value.Immutable
@ImmutableProtocol(builder = ImmutableManagedScriptDefinitionRecordValue.Builder.class)
public interface ManagedScriptDefinitionRecordValue extends RecordValue, TenantOwned {

  long getManagedScriptDefinitionKey();

  ManagedScriptDefinitionStatus getStatus();

  long getRevision();

  long getResourceKey();

  String getResourceName();

  byte[] getArtifactDigest();

  String getElementId();

  String getBpmnProcessId();

  long getProcessDefinitionKey();

  int getProcessDefinitionVersion();

  String getProcessDefinitionVersionTag();

  String getLanguage();

  String getRuntime();

  String getProvider();

  String getLeaseOwner();

  String getLeaseToken();

  long getLeaseExpiresAt();

  long getLeaseDuration();

  String getProviderOperationId();

  String getProviderDeploymentId();

  String getFailureCode();

  String getFailureMessage();

  boolean isRetryable();

  String getOperationId();

  @Override
  String getTenantId();
}
