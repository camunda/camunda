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
package io.camunda.client.api.search.filter.builder;

/** Advanced filter on a single metadata value. Values are strings or numbers. */
public interface ClusterVariableMetadataProperty
    extends PropertyBase<Object, ClusterVariableMetadataProperty> {

  /** The metadata value is greater than the given value. */
  ClusterVariableMetadataProperty gt(final Number value);

  /** The metadata value is greater than or equal to the given value. */
  ClusterVariableMetadataProperty gte(final Number value);

  /** The metadata value is lower than the given value. */
  ClusterVariableMetadataProperty lt(final Number value);

  /** The metadata value is lower than or equal to the given value. */
  ClusterVariableMetadataProperty lte(final Number value);

  /** The metadata value matches the given pattern. */
  ClusterVariableMetadataProperty like(final String value);
}
