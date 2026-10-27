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
package io.atomix.cluster;

import io.atomix.utils.net.Address;
import java.util.Properties;

/** Member builder. */
public class MemberBuilder extends NodeBuilder {
  protected final MemberConfig config;

  protected MemberBuilder(final MemberConfig config) {
    super(config);
    this.config = config;
  }

  @Override
  public MemberBuilder withId(final String id) {
    super.withId(id);
    return this;
  }

  @Override
  public MemberBuilder withId(final NodeId id) {
    super.withId(id);
    return this;
  }

  @Override
  public MemberBuilder withHost(final String host) {
    super.withHost(host);
    return this;
  }

  @Override
  public MemberBuilder withPort(final int port) {
    super.withPort(port);
    return this;
  }

  /**
   * Sets the member address.
   *
   * @param address the member address
   * @return the member builder
   */
  @Override
  public MemberBuilder withAddress(final Address address) {
    config.setAddress(address);
    return this;
  }

  @Override
  public Member build() {
    return new Member(config);
  }

  /**
   * Sets the member identifier.
   *
   * @param id the member identifier
   * @return the member builder
   */
  public MemberBuilder withId(final MemberId id) {
    config.setId(id);
    return this;
  }

  /**
   * Sets the nodeVersion used to discriminate different instances for the same node, where a static
   * node id is not provided.
   *
   * @param nodeVersion the node version to set
   * @return the member builder
   */
  public MemberBuilder withNodeVersion(final long nodeVersion) {
    config.setNodeVersion(nodeVersion);
    return this;
  }

  /**
   * Sets the zone to which the member belongs.
   *
   * @param zoneId the zone to which the member belongs
   * @return the member builder
   */
  public MemberBuilder withZoneId(final String zoneId) {
    config.setZoneId(zoneId);
    return this;
  }

  /**
   * Sets the rack to which the member belongs.
   *
   * @param rack the rack to which the member belongs
   * @return the member builder
   */
  public MemberBuilder withRackId(final String rack) {
    config.setRackId(rack);
    return this;
  }

  /**
   * Sets the host to which the member belongs.
   *
   * @param hostId the host to which the member belongs
   * @return the member builder
   */
  public MemberBuilder withHostId(final String hostId) {
    config.setHostId(hostId);
    return this;
  }

  /**
   * Sets the member properties.
   *
   * @param properties the member properties
   * @return the member builder
   * @throws NullPointerException if the properties are null
   */
  public MemberBuilder withProperties(final Properties properties) {
    config.setProperties(properties);
    return this;
  }
}
