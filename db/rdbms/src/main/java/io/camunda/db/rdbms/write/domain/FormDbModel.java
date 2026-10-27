/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record FormDbModel(
    Long formKey, String tenantId, String formId, String schema, Long version, Boolean isDeleted)
    implements DbModel<FormDbModel> {

  @Override
  public FormDbModel copy(
      final Function<ObjectBuilder<FormDbModel>, ObjectBuilder<FormDbModel>> builderFunction) {
    return builderFunction
        .apply(
            new FormDbModelBuilder()
                .formKey(formKey)
                .tenantId(tenantId)
                .formId(formId)
                .schema(schema)
                .version(version)
                .isDeleted(isDeleted))
        .build();
  }

  public static class FormDbModelBuilder implements ObjectBuilder<FormDbModel> {

    private Long formKey;
    private String tenantId;
    private String formId;
    private String schema;
    private Long version;
    private Boolean isDeleted;

    public FormDbModelBuilder() {}

    public FormDbModelBuilder formKey(final Long formKey) {
      this.formKey = formKey;
      return this;
    }

    public FormDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public FormDbModelBuilder formId(final String formId) {
      this.formId = formId;
      return this;
    }

    public FormDbModelBuilder schema(final String schema) {
      this.schema = schema;
      return this;
    }

    public FormDbModelBuilder version(final Long version) {
      this.version = version;
      return this;
    }

    public FormDbModelBuilder isDeleted(final Boolean isDeleted) {
      this.isDeleted = isDeleted;
      return this;
    }

    @Override
    public FormDbModel build() {
      return new FormDbModel(formKey, tenantId, formId, schema, version, isDeleted);
    }
  }
}
