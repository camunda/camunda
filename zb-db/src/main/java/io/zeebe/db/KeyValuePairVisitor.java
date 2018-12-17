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
package io.zeebe.db;

/**
 * Represents an function that accepts a zeebe key value pair and produces an primitive boolean as
 * result.
 *
 * @param <KeyType> the type of the key
 * @param <ValueType> the type of the value
 */
@FunctionalInterface
public interface KeyValuePairVisitor<KeyType extends DbKey, ValueType extends DbValue> {

  /**
   * Visits the zeebe key value pair. The result indicates whether it should visit more key-value
   * pairs or not.
   *
   * @param key the key
   * @param value the value
   * @return true if the visiting should continue, false otherwise
   */
  boolean visit(KeyType key, ValueType value);
}
