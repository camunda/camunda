/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

public class OptimizeDependency {

  private String projectName;
  private String projectVersion;
  private String licenseName;
  private String licenseLink;

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public String getProjectVersion() {
    return projectVersion;
  }

  public void setProjectVersion(String projectVersion) {
    this.projectVersion = projectVersion;
  }

  public String getLicenseName() {
    return licenseName;
  }

  public void setLicenseName(String licenseName) {
    this.licenseName = licenseName;
  }

  public String getLicenseLink() {
    return licenseLink;
  }

  public void setLicenseLink(String licenseLink) {
    this.licenseLink = licenseLink;
  }

  public String toMarkDown() {
    if (validString(licenseLink)) {
      return "* "
          + projectName
          + "@"
          + projectVersion
          + ", "
          + "[("
          + licenseName
          + ")]"
          + "("
          + licenseLink
          + ")\n";
    } else {
      return "* " + projectName + "@" + projectVersion + ", " + "(" + licenseName + ")\n";
    }
  }

  public boolean isProperLicense() {
    return validString(licenseName) && validString(projectName);
  }

  private boolean validString(String str) {
    return str != null && !str.isEmpty();
  }
}
