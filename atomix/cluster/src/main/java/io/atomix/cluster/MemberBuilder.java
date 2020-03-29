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
   * @param address a host:port tuple
   * @return the member builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withHost(String)} and/or {@link #withPort(int)} instead
   */
  @Deprecated
  public MemberBuilder withAddress(final String address) {
    return withAddress(Address.from(address));
  }

  /**
   * Sets the member host/port.
   *
   * @param host the host name
   * @param port the port number
   * @return the member builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withHost(String)} and {@link #withPort(int)} instead
   */
  @Deprecated
  public MemberBuilder withAddress(final String host, final int port) {
    return withAddress(Address.from(host, port));
  }

  /**
   * Sets the member address using local host.
   *
   * @param port the port number
   * @return the member builder
   * @throws io.atomix.utils.net.MalformedAddressException if a valid {@link Address} cannot be
   *     constructed from the arguments
   * @deprecated since 3.1. Use {@link #withPort(int)} instead
   */
  @Deprecated
  public MemberBuilder withAddress(final int port) {
    return withAddress(Address.from(port));
  }

  /**
   * Sets the member address.
   *
   * @param address the member address
   * @return the member builder
   */
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
   * Sets the zone to which the member belongs.
   *
   * @param zone the zone to which the member belongs
   * @return the member builder
   * @deprecated since 3.1. Use {@link #withZoneId(String)} instead
   */
  @Deprecated
  public MemberBuilder withZone(final String zone) {
    config.setZoneId(zone);
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
   * Sets the rack to which the member belongs.
   *
   * @param rack the rack to which the member belongs
   * @return the member builder
   * @deprecated since 3.1. Use {@link #withRackId(String)} instead
   */
  @Deprecated
  public MemberBuilder withRack(final String rack) {
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

  /**
   * Sets a member property.
   *
   * @param key the property key to set
   * @param value the property value to set
   * @return the member builder
   * @throws NullPointerException if the property is null
   */
  public MemberBuilder withProperty(final String key, final String value) {
    config.setProperty(key, value);
    return this;
  }
}
