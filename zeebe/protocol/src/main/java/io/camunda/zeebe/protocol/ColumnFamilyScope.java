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
package io.camunda.zeebe.protocol;

/**
 * Identifies whether a Column Family contains data that must be available on all partitions or just
 * on one.
 *
 * <p>As a rule of thumb, if a Column Family contains:
 *
 * <ul>
 *   <li>process definitions, decisions definitions, forms definitions or identity entities they
 *       should be GLOBAL.
 *   <li>an instance of any kind, e.g. process instance, variables ecc, then it's likely that it's a
 *       PARTITION_LOCAL Column Family.
 * </ul>
 *
 * <p>Note that a Column Family should not contain both: if that is the case, then the Column Family
 * should be split in two.
 *
 * <p>This information is crucial to be able to correctly bootstrap a new partition based on the
 * state of another partition.
 */
public enum ColumnFamilyScope {
  /* All partitions need this data*/
  GLOBAL,
  /* Only one partition  */
  PARTITION_LOCAL;
}
