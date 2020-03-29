/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.core.profile;

import io.atomix.core.AtomixConfig;

/** Client profile. */
public class ClientProfile implements Profile {
  public static final Type TYPE = new Type();
  private final ClientProfileConfig config;

  ClientProfile() {
    this(new ClientProfileConfig());
  }

  ClientProfile(final ClientProfileConfig config) {
    this.config = config;
  }

  @Override
  public ClientProfileConfig config() {
    return config;
  }

  @Override
  public void configure(final AtomixConfig config) {
    // Do nothing! This profile is just for code readability.
  }

  /** Client profile type. */
  public static class Type implements Profile.Type<ClientProfileConfig> {
    private static final String NAME = "client";

    @Override
    public String name() {
      return NAME;
    }

    @Override
    public ClientProfileConfig newConfig() {
      return new ClientProfileConfig();
    }

    @Override
    public Profile newProfile(final ClientProfileConfig config) {
      return new ClientProfile();
    }
  }
}
