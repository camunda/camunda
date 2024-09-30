/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.operate;

import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.List;

/** TODO To be extended */
public class ListViewTemplate implements IndexTemplateDescriptor {

  public static final String INDEX_NAME = "list-view";

  public static final String JOIN_RELATION = "joinRelation";
  public static final String PROCESS_INSTANCE_JOIN_RELATION = "processInstance";
  public static final String ACTIVITIES_JOIN_RELATION =
      "activity"; // now we call it flow node instance
  public static final String VARIABLES_JOIN_RELATION = "variable";

  @Override
  public String getFullQualifiedName() {
    return null;
  }

  @Override
  public String getAlias() {
    return null;
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getMappingsClasspathFilename() {
    return null;
  }

  @Override
  public String getAllVersionsIndexNameRegexPattern() {
    return null;
  }

  @Override
  public String getIndexPattern() {
    return null;
  }

  @Override
  public String getTemplateName() {
    return null;
  }

  @Override
  public List<String> getComposedOf() {
    return null;
  }
}
