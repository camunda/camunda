/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.db.rdbms.write.domain.FormDbModel.FormDbModelBuilder;
import java.util.List;
import java.util.function.Function;

public final class FormFixtures extends CommonFixtures {

  private FormFixtures() {}

  public static FormDbModel createRandomized() {
    return createRandomized(b -> b);
  }

  public static FormDbModel createRandomized(
      final Function<FormDbModelBuilder, FormDbModelBuilder> builderFunction) {
    final Long formKey = nextKey();
    final var builder =
        new FormDbModelBuilder()
            .formKey(formKey)
            .tenantId("tenant-" + formKey)
            .formId(nextStringId())
            .schema("schema-" + formKey)
            .version(RANDOM.nextLong(1000))
            .isDeleted(false);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomForms(
      final RdbmsService rdbmsService, final String formId) {
    createAndSaveRandomForms(rdbmsService, b -> b.formId(formId));
  }

  public static void createAndSaveRandomForms(
      final RdbmsService rdbmsService,
      final Function<FormDbModelBuilder, FormDbModelBuilder> builderFunction) {
    createAndSaveRandomForms(rdbmsService, 20, builderFunction);
  }

  public static void createAndSaveRandomForms(
      final RdbmsService rdbmsService,
      final int numberOfInstances,
      final Function<FormDbModelBuilder, FormDbModelBuilder> builderFunction) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(1L);
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters.getFormWriter().create(FormFixtures.createRandomized(builderFunction));
    }
    rdbmsWriters.flush();
  }

  public static void createAndSaveForm(final RdbmsService rdbmsService, final FormDbModel form) {
    createAndSaveForms(rdbmsService, List.of(form));
  }

  public static void createAndSaveForms(
      final RdbmsService rdbmsService, final List<FormDbModel> formList) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(1L);
    for (final FormDbModel form : formList) {
      rdbmsWriters.getFormWriter().create(form);
    }
    rdbmsWriters.flush();
  }
}
