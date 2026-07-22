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

import java.util.List;

/** Advanced filter on a single metadata value. Values are strings or numbers. */
public interface MetadataValueProperty {

  /** The metadata value equals the given value. */
  MetadataValueProperty eq(final Object value);

  /** The metadata value does not equal the given value. */
  MetadataValueProperty neq(final Object value);

  /** The metadata key exists (or not). */
  MetadataValueProperty exists(final boolean value);

  /** The metadata value is greater than the given value. */
  MetadataValueProperty gt(final Number value);

  /** The metadata value is greater than or equal to the given value. */
  MetadataValueProperty gte(final Number value);

  /** The metadata value is lower than the given value. */
  MetadataValueProperty lt(final Number value);

  /** The metadata value is lower than or equal to the given value. */
  MetadataValueProperty lte(final Number value);

  /** The metadata value matches any of the given values. */
  MetadataValueProperty in(final List<Object> values);

  /** The metadata value matches any of the given values. */
  MetadataValueProperty in(final Object... values);

  /** The metadata value matches the given pattern. */
  MetadataValueProperty like(final String value);
}
