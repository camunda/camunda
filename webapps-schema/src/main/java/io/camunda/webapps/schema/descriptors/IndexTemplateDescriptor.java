/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import java.util.List;

public interface IndexTemplateDescriptor extends IndexDescriptor {

  String PARTITION_ID = "partitionId";
  String POSITION = "position";

  /**
   * Starts the suffix of an ordinal index, which holds runtime data next to the main index, e.g.
   * {@code <full qualified name>ord00001}.
   */
  String ORDINAL_SUFFIX_START = "ord";

  String getIndexPattern();

  String getTemplateName();

  List<String> getComposedOf();
}
