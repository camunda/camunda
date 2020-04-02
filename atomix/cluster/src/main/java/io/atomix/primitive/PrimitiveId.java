/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive;

import com.google.common.hash.Hashing;
import io.atomix.utils.AbstractIdentifier;
import java.nio.charset.StandardCharsets;

/** Snapshot identifier. */
public class PrimitiveId extends AbstractIdentifier<Long> {

  public PrimitiveId(final Long value) {
    super(value);
  }

  /**
   * Creates a snapshot ID from the given number.
   *
   * @param id the number from which to create the identifier
   * @return the snapshot identifier
   */
  public static PrimitiveId from(final long id) {
    return new PrimitiveId(id);
  }

  /**
   * Creates a snapshot ID from the given string.
   *
   * @param id the string from which to create the identifier
   * @return the snapshot identifier
   */
  public static PrimitiveId from(final String id) {
    return from(Hashing.sha256().hashString(id, StandardCharsets.UTF_8).asLong());
  }
}
