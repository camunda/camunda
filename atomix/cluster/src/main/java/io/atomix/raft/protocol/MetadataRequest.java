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
package io.atomix.raft.protocol;

/** Cluster metadata request. */
public class MetadataRequest extends SessionRequest {

  public MetadataRequest(final long session) {
    super(session);
  }

  /**
   * Returns a new metadata request builder.
   *
   * @return A new metadata request builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Metadata request builder. */
  public static class Builder extends SessionRequest.Builder<Builder, MetadataRequest> {

    @Override
    public MetadataRequest build() {
      return new MetadataRequest(session);
    }
  }
}
