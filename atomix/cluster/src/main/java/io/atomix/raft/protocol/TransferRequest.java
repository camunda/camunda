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
package io.atomix.raft.protocol;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.cluster.MemberId;
import java.util.Objects;

/** Leadership transfer request. */
public class TransferRequest extends AbstractRaftRequest {

  protected final MemberId member;

  protected TransferRequest(final MemberId member) {
    this.member = member;
  }

  /**
   * Returns a new transfer request builder.
   *
   * @return A new transfer request builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the member to which to transfer.
   *
   * @return The member to which to transfer.
   */
  public MemberId member() {
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

    return ((TransferRequest) object).member.equals(member);
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("member", member).toString();
  }

  /** Transfer request builder. */
  public static class Builder extends AbstractRaftRequest.Builder<Builder, TransferRequest> {

    protected MemberId member;

    /**
     * Sets the request member.
     *
     * @param member The request member.
     * @return The request builder.
     * @throws NullPointerException if {@code member} is null
     */
    @SuppressWarnings("unchecked")
    public Builder withMember(final MemberId member) {
      this.member = checkNotNull(member, "member cannot be null");
      return this;
    }

    @Override
    protected void validate() {
      super.validate();
      checkNotNull(member, "member cannot be null");
    }

    @Override
    public TransferRequest build() {
      return new TransferRequest(member);
    }
  }
}
