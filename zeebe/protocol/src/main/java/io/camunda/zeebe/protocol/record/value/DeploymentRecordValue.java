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
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.util.List;
import org.immutables.value.Value;

/**
 * Represents a single deployment event or command.
 *
 * <p>See {@link DeploymentIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableDeploymentRecordValue.Builder.class)
public interface DeploymentRecordValue extends RecordValue, TenantOwned {
  /**
   * @return the resources to deploy
   */
  List<DeploymentResource> getResources();

  /**
   * @return the deployed processes
   */
  List<ProcessMetadataValue> getProcessesMetadata();

  /**
   * @return the deployed decisions
   */
  List<DecisionRecordValue> getDecisionsMetadata();

  /**
   * @return the deployed decision requirements (DRGs)
   */
  List<DecisionRequirementsMetadataValue> getDecisionRequirementsMetadata();

  /**
   * @return the deployed forms
   */
  List<FormMetadataValue> getFormMetadata();

  /**
   * @return the unique key of the deployment
   */
  long getKey();
}
