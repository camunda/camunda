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
 * Interface for enum values that have a numeric value associated with them. This is used to avoid
 * using the ordinal value of an enum, which is fragile when the enum values are reordered.
 *
 * <p>In the Zeebe repo, reordering of enum values can typically occur by accident when backporting
 * additions to the enum. While we try our best to add values to the bottom of the enum, backporting
 * can still cause reordering issues. This is because while one addition may be backported, another
 * could easily be missed. This would result in the backported enum having a different ordinal value
 * than the original, which would cause issues when serializing/deserializing.
 *
 * <p>For example, if we have an enum with values A, B, C, D, and E, and we backport the addition of
 * E to an older version of the enum, we may accidentally miss backporting the addition of D. This
 * would result in the backported enum having values A, B, C, E. The value associated with E would
 * differ semantically but have the same ordinal between the original and backported enum.
 *
 * <p>By using this interface, we can avoid this issue by explicitly setting the value associated.
 */
public interface EnumValue {

  /** Returns the numeric value associated with this enum value. */
  int getValue();
}
