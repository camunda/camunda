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
package io.camunda.zeebe.protocol.record.value.deployment;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import org.immutables.value.Value;

/**
 * The metadata of a deployed decision requirements graph (DRG/DRD). A DRG represents the DMN
 * resource and all decisions of the DMN belongs to it. The metadata contains relevant properties of
 * the DMN resource, except the binary DMN resource itself.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableDecisionRequirementsMetadataValue.Builder.class)
public interface DecisionRequirementsMetadataValue {

  /**
   * @return the ID of the DRG in the DMN
   */
  String getDecisionRequirementsId();

  /**
   * @return the name of the DRG in the DMN
   */
  String getDecisionRequirementsName();

  /**
   * @return the version of the deployed DRG
   */
  int getDecisionRequirementsVersion();

  /**
   * @return the key of the deployed DRG
   */
  long getDecisionRequirementsKey();

  /**
   * @return the namespace of the DRG in the DMN
   */
  String getNamespace();

  /**
   * @return the name of the resource through which this DRG was deployed
   */
  String getResourceName();

  /**
   * @return the checksum of the DMN resource (MD5)
   */
  byte[] getChecksum();

  /**
   * @return {@code true} if the DRG is a duplicate (and has been deployed previously), otherwise
   *     {@code false}
   */
  boolean isDuplicate();
}
