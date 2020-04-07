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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.raft.RaftError;
import io.atomix.raft.cluster.RaftMember;
import java.util.Collection;
import java.util.Objects;

/** Server configuration response. */
public abstract class ConfigurationResponse extends AbstractRaftResponse {

  protected final long index;
  protected final long term;
  protected final long timestamp;
  protected final Collection<RaftMember> members;

  public ConfigurationResponse(
      final Status status,
      final RaftError error,
      final long index,
      final long term,
      final long timestamp,
      final Collection<RaftMember> members) {
    super(status, error);
    this.index = index;
    this.term = term;
    this.timestamp = timestamp;
    this.members = members;
  }

  /**
   * Returns the response index.
   *
   * @return The response index.
   */
  public long index() {
    return index;
  }

  /**
   * Returns the configuration term.
   *
   * @return The configuration term.
   */
  public long term() {
    return term;
  }

  /**
   * Returns the response configuration time.
   *
   * @return The response time.
   */
  public long timestamp() {
    return timestamp;
  }

  /**
   * Returns the configuration members list.
   *
   * @return The configuration members list.
   */
  public Collection<RaftMember> members() {
    return members;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), status, index, term, members);
  }

  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || !getClass().isAssignableFrom(object.getClass())) {
      return false;
    }

    final ConfigurationResponse response = (ConfigurationResponse) object;
    return response.status == status
        && response.index == index
        && response.term == term
        && response.timestamp == timestamp
        && response.members.equals(members);
  }

  @Override
  public String toString() {
    if (status == Status.OK) {
      return toStringHelper(this)
          .add("status", status)
          .add("index", index)
          .add("term", term)
          .add("timestamp", timestamp)
          .add("members", members)
          .toString();
    } else {
      return toStringHelper(this).add("status", status).add("error", error).toString();
    }
  }

  /** Configuration response builder. */
  public abstract static class Builder<T extends Builder<T, U>, U extends ConfigurationResponse>
      extends AbstractRaftResponse.Builder<T, U> {

    protected long index;
    protected long term;
    protected long timestamp;
    protected Collection<RaftMember> members;

    /**
     * Sets the response index.
     *
     * @param index The response index.
     * @return The response builder.
     * @throws IllegalArgumentException if {@code index} is negative
     */
    @SuppressWarnings("unchecked")
    public T withIndex(final long index) {
      checkArgument(index >= 0, "index must be positive");
      this.index = index;
      return (T) this;
    }

    /**
     * Sets the response term.
     *
     * @param term The response term.
     * @return The response builder.
     * @throws IllegalArgumentException if {@code term} is negative
     */
    @SuppressWarnings("unchecked")
    public T withTerm(final long term) {
      checkArgument(term >= 0, "term must be positive");
      this.term = term;
      return (T) this;
    }

    /**
     * Sets the response time.
     *
     * @param time The response time.
     * @return The response builder.
     * @throws IllegalArgumentException if {@code time} is negative
     */
    @SuppressWarnings("unchecked")
    public T withTime(final long time) {
      checkArgument(time > 0, "time must be positive");
      this.timestamp = time;
      return (T) this;
    }

    /**
     * Sets the response members.
     *
     * @param members The response members.
     * @return The response builder.
     * @throws NullPointerException if {@code members} is null
     */
    @SuppressWarnings("unchecked")
    public T withMembers(final Collection<RaftMember> members) {
      this.members = checkNotNull(members, "members cannot be null");
      return (T) this;
    }

    @Override
    protected void validate() {
      super.validate();
      if (status == Status.OK) {
        checkArgument(index >= 0, "index must be positive");
        checkArgument(term >= 0, "term must be positive");
        checkArgument(timestamp > 0, "time must be positive");
        checkNotNull(members, "members cannot be null");
      }
    }
  }
}
