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
import java.util.Map;
import java.util.Properties;

/** Member configuration. */
public class MemberConfig extends NodeConfig {
  private MemberId id = MemberId.anonymous();
  private String zoneId;
  private String rackId;
  private String hostId;
  private Properties properties = new Properties();

  /**
   * Returns the member identifier.
   *
   * @return the member identifier
   */
  public MemberId getId() {
    return id;
  }

  /**
   * Sets the member identifier.
   *
   * @param id the member identifier
   * @return the member configuration
   */
  public MemberConfig setId(final String id) {
    return setId(id != null ? MemberId.from(id) : null);
  }

  @Override
  public MemberConfig setId(final NodeId id) {
    return setId(id != null ? MemberId.from(id.id()) : null);
  }

  /**
   * Sets the member identifier.
   *
   * @param id the member identifier
   * @return the member configuration
   */
  public MemberConfig setId(final MemberId id) {
    this.id = id != null ? id : MemberId.anonymous();
    return this;
  }

  @Override
  public MemberConfig setHost(final String host) {
    super.setHost(host);
    setHostId(host);
    return this;
  }

  @Override
  public MemberConfig setPort(final int port) {
    super.setPort(port);
    return this;
  }

  @Override
  public MemberConfig setAddress(final String address) {
    super.setAddress(address);
    return this;
  }

  @Override
  public MemberConfig setAddress(final Address address) {
    super.setAddress(address);
    return this;
  }

  /**
   * Returns the member zone.
   *
   * @return the member zone
   */
  public String getZoneId() {
    return zoneId;
  }

  /**
   * Sets the member zone.
   *
   * @param zoneId the member zone
   * @return the member configuration
   */
  public MemberConfig setZoneId(final String zoneId) {
    this.zoneId = zoneId;
    return this;
  }

  /**
   * Sets the member zone.
   *
   * @param zone the member zone
   * @return the member configuration
   */
  public MemberConfig setZone(final String zone) {
    return setZoneId(zone);
  }

  /**
   * Returns the member rack.
   *
   * @return the member rack
   */
  public String getRackId() {
    return rackId;
  }

  /**
   * Sets the member rack.
   *
   * @param rackId the member rack
   * @return the member configuration
   */
  public MemberConfig setRackId(final String rackId) {
    this.rackId = rackId;
    return this;
  }

  /**
   * Sets the member rack.
   *
   * @param rack the member rack
   * @return the member configuration
   */
  public MemberConfig setRack(final String rack) {
    return setRackId(rack);
  }

  /**
   * Returns the member host.
   *
   * @return the member host
   */
  public String getHostId() {
    return hostId;
  }

  /**
   * Sets the member host.
   *
   * @param hostId the member host
   * @return the member configuration
   */
  public MemberConfig setHostId(final String hostId) {
    this.hostId = hostId;
    return this;
  }

  /**
   * Returns the member properties.
   *
   * @return the member properties
   */
  public Properties getProperties() {
    return properties;
  }

  /**
   * Sets the member properties.
   *
   * @param map the member properties
   * @return the member configuration
   */
  public MemberConfig setProperties(final Map<String, String> map) {
    final Properties properties = new Properties();
    properties.putAll(map);
    return setProperties(properties);
  }

  /**
   * Sets the member properties.
   *
   * @param properties the member properties
   * @return the member configuration
   */
  public MemberConfig setProperties(final Properties properties) {
    this.properties = properties;
    return this;
  }

  /**
   * Sets a member property.
   *
   * @param key the property key to et
   * @param value the property value to et
   * @return the member configuration
   */
  public MemberConfig setProperty(final String key, final String value) {
    this.properties.put(key, value);
    return this;
  }
}
