/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GeneratorConfiguration {

  private final List<BpmnFeature> includeFeatures = new ArrayList<>();
  private final List<BpmnFeature> excludeFeatures = new ArrayList<>();

  public List<BpmnFeature> getIncludeFeatures() {
    return includeFeatures;
  }

  public List<BpmnFeature> getExcludeFeatures() {
    return excludeFeatures;
  }

  public GeneratorConfiguration withFeatures(final BpmnFeature... features) {
    includeFeatures.addAll(Arrays.asList(features));
    return this;
  }

  public GeneratorConfiguration excludeFeatures(final BpmnFeature... features) {
    excludeFeatures.addAll(Arrays.asList(features));
    return this;
  }
}
