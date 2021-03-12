/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.raft.storage.log.entry;

/**
 * Indicates a leader change has occurred.
 *
 * <p>The {@code InitialEntry} is logged by a leader at the beginning of its term to indicate that a
 * leadership change has occurred. Initialize entries are always the first entry to be committed at
 * the start of a leader's term.
 */
public class InitialEntry implements RaftEntry {

  @Override
  public int hashCode() {
    return 1;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "InitialEntry{}";
  }
}
