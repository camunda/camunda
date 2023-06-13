/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionRequirements {

  private String id;
  private Long key;
  private String decisionRequirementsId;
  private String name;
  private Integer version;
  private String resourceName;
  private String xml;

  public String getId() {
    return id;
  }

  public DecisionRequirements setId(String id) {
    this.id = id;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public DecisionRequirements setKey(long key) {
    this.key = key;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionRequirements setDecisionRequirementsId(String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionRequirements setName(String name) {
    this.name = name;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public DecisionRequirements setVersion(int version) {
    this.version = version;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public DecisionRequirements setResourceName(String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  public String getXml() {
    return xml;
  }

  public DecisionRequirements setXml(String xml) {
    this.xml = xml;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    DecisionRequirements that = (DecisionRequirements) o;
    return Objects.equals(id, that.id) && Objects.equals(key, that.key) && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(name, that.name) && Objects.equals(version, that.version) && Objects.equals(resourceName, that.resourceName) && Objects.equals(xml,
        that.xml);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, key, decisionRequirementsId, name, version, resourceName, xml);
  }

  @Override
  public String toString() {
    return "DecisionRequirements{" + "id='" + id + '\'' + ", key=" + key + ", decisionRequirementsId='" + decisionRequirementsId + '\'' + ", name='" + name
        + '\'' + ", version=" + version + ", resourceName='" + resourceName + '\'' + ", xml='" + xml + '\'' + '}';
  }
}
