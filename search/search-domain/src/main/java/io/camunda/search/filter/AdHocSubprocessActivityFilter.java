/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;

public record AdHocSubprocessActivityFilter(Long processDefinitionKey, String adHocSubprocessId)
    implements FilterBase {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder implements ObjectBuilder<AdHocSubprocessActivityFilter> {

    private Long processDefinitionKey;
    private String adHocSubprocessId;

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder adHocSubprocessId(final String adHocSubprocessId) {
      this.adHocSubprocessId = adHocSubprocessId;
      return this;
    }

    @Override
    public AdHocSubprocessActivityFilter build() {
      return new AdHocSubprocessActivityFilter(processDefinitionKey, adHocSubprocessId);
    }
  }
}
