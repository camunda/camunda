/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
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
    final var builder =
        new FormDbModelBuilder()
            .formKey(nextKey())
            .tenantId("tenant-" + RANDOM.nextInt(1000))
            .formId(nextStringId())
            .schema("schema-" + RANDOM.nextInt(1000))
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
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(1L);
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getFormWriter().create(FormFixtures.createRandomized(builderFunction));
    }
    rdbmsWriter.flush();
  }

  public static void createAndSaveForm(final RdbmsService rdbmsService, final FormDbModel form) {
    createAndSaveForms(rdbmsService, List.of(form));
  }

  public static void createAndSaveForms(
      final RdbmsService rdbmsService, final List<FormDbModel> formList) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(1L);
    for (final FormDbModel form : formList) {
      rdbmsWriter.getFormWriter().create(form);
    }
    rdbmsWriter.flush();
  }
}
