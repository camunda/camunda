/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import java.util.Objects;

public class C8AppLink {

  @GraphQLField @GraphQLNonNull private String name;
  @GraphQLField @GraphQLNonNull private String link;

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
