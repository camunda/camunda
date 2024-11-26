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
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableFormMetadataValue.Builder.class)
public interface RpaMetadataValue extends RecordValue, TenantOwned {

  /**
   * @return the ID of the RPA script
   */
  String getRpaId();

  /**
   * @return the version of the deployed RPA script
   */
  int getVersion();

  /**
   * @return the custom version tag of the RPA script
   */
  String getVersionTag();

  /**
   * @return the key of the deployed RPA script
   */
  long getRpaKey();

  /**
   * @return the checksum of the RPA resource (MD5)
   */
  byte[] getChecksum();

  /**
   * @return {@code true} if the RPA is a duplicate (and has been deployed previously), otherwise
   *     {@code false}
   */
  boolean isDuplicate();

  /**
   * @return the key of the deployment this form was deployed with
   */
  long getDeploymentKey();
}
