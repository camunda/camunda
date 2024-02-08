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

import io.atomix.raft.RaftError;

/** Force Configuration response. */
public class ForceConfigureResponse extends AbstractRaftResponse {

  // TODO: To check if we have to do reject request to ensure that receiver do not overwrite it's
  // latest configuration with an outdated configuration from the requests. May be respond with the
  // current the configuration if the requester has an older configuration.
  public ForceConfigureResponse(final Status status, final RaftError error) {
    super(status, error);
  }

  /**
   * Returns a new configure response builder.
   *
   * @return A new configure response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder
      extends AbstractRaftResponse.Builder<Builder, ForceConfigureResponse> {

    @Override
    public ForceConfigureResponse build() {
      return new ForceConfigureResponse(status, error);
    }
  }
}
