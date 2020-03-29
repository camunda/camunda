/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.primitive;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Test member. */
public class TestMember implements RaftMember {

  private final MemberId memberId;
  private final Type type;

  public TestMember(final MemberId memberId, final Type type) {
    this.memberId = memberId;
    this.type = type;
  }

  @Override
  public MemberId memberId() {
    return memberId;
  }

  @Override
  public int hash() {
    return 0;
  }

  @Override
  public void addTypeChangeListener(final Consumer<Type> listener) {}

  @Override
  public void removeTypeChangeListener(final Consumer<Type> listener) {}

  @Override
  public CompletableFuture<Void> promote() {
    return null;
  }

  @Override
  public CompletableFuture<Void> promote(final Type type) {
    return null;
  }

  @Override
  public CompletableFuture<Void> demote() {
    return null;
  }

  @Override
  public CompletableFuture<Void> demote(final Type type) {
    return null;
  }

  @Override
  public CompletableFuture<Void> remove() {
    return null;
  }

  @Override
  public Instant getLastUpdated() {
    return null;
  }

  @Override
  public Type getType() {
    return type;
  }
}
