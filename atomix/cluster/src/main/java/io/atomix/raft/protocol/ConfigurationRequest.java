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
 * limitations under the License
 */
package io.atomix.raft.protocol;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.raft.cluster.RaftMember;
import java.util.Objects;

/**
 * Configuration change request.
 *
 * <p>Configuration change requests are the basis for members joining and leaving the cluster. When
 * a member wants to join or leave the cluster, it must submit a configuration change request to the
 * leader where the change will be logged and replicated.
 */
public abstract class ConfigurationRequest extends AbstractRaftRequest {

  protected final RaftMember member;

  protected ConfigurationRequest(final RaftMember member) {
    this.member = member;
  }

  /**
   * Returns the member to configure.
   *
   * @return The member to configure.
   */
  public RaftMember member() {
    return member;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), member);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || !getClass().isAssignableFrom(object.getClass())) {
      return false;
    }

    return ((ConfigurationRequest) object).member.equals(member);
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("member", member).toString();
  }

  /** Configuration request builder. */
  public abstract static class Builder<T extends Builder<T, U>, U extends ConfigurationRequest>
      extends AbstractRaftRequest.Builder<T, U> {

    protected RaftMember member;

    /**
     * Sets the request member.
     *
     * @param member The request member.
     * @return The request builder.
     * @throws NullPointerException if {@code member} is null
     */
    @SuppressWarnings("unchecked")
    public T withMember(final RaftMember member) {
      this.member = checkNotNull(member, "member cannot be null");
      return (T) this;
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(member, "member cannot be null");
    }
  }
}
