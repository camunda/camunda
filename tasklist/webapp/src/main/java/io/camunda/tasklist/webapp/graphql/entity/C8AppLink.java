/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import java.util.Objects;

public class C8AppLink {

  private String name;
  private String link;

  public String getName() {
    return name.toLowerCase();
  }

  public C8AppLink setName(String name) {
    this.name = name.toLowerCase();
    return this;
  }

  public String getLink() {
    return link;
  }

  public C8AppLink setLink(String link) {
    this.link = link;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final C8AppLink c8AppLink = (C8AppLink) o;
    return Objects.equals(name, c8AppLink.name) && Objects.equals(link, c8AppLink.link);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, link);
  }

  @Override
  public String toString() {
    return "C8AppLink{" + "name='" + name + '\'' + ", link='" + link + '\'' + '}';
  }
}
