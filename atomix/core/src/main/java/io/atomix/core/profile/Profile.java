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
import io.atomix.utils.ConfiguredType;
import io.atomix.utils.config.Configured;
import java.util.Collection;

/** Atomix profile. */
public interface Profile extends Configured<ProfileConfig> {

  /**
   * Creates a consensus profile.
   *
   * @param members the consensus members
   * @return the consensus profile
   */
  static Profile consensus(final String... members) {
    return new ConsensusProfile(members);
  }

  /**
   * Creates a consensus profile.
   *
   * @param members the consensus members
   * @return the consensus profile
   */
  static Profile consensus(final Collection<String> members) {
    return new ConsensusProfile(members);
  }

  /**
   * Creates a new client profile.
   *
   * @return a new client profile
   */
  static Profile client() {
    return new ClientProfile();
  }

  /**
   * Configures the Atomix instance.
   *
   * @param config the Atomix configuration
   */
  void configure(AtomixConfig config);

  /** Profile type. */
  interface Type<C extends ProfileConfig> extends ConfiguredType<C> {

    /**
     * Creates a new instance of the profile.
     *
     * @param config the profile configuration
     * @return the profile instance
     */
    Profile newProfile(C config);
  }
}
