/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.form;

import static io.camunda.it.rdbms.db.fixtures.FormFixtures.createAndSaveForm;
import static io.camunda.it.rdbms.db.fixtures.FormFixtures.createAndSaveRandomForms;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.FormReader;
import io.camunda.db.rdbms.write.domain.FormDbModel;
import io.camunda.db.rdbms.write.domain.FormDbModel.FormDbModelBuilder;
import io.camunda.it.rdbms.db.fixtures.FormFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.filter.FormFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.FormQuery;
import io.camunda.search.sort.FormSort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class FormIT {

  @TestTemplate
  public void shouldSaveAndFindFormByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final FormDbModel randomizedForm = FormFixtures.createRandomized();
    createAndSaveForm(rdbmsService, randomizedForm);

    final var form = rdbmsService.getFormReader().findOne(randomizedForm.formKey()).orElse(null);
    assertFormEntity(form, randomizedForm);
  }

  @TestTemplate
  public void shouldUpdateAndFindFormByKey(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final FormDbModel randomizedForm = FormFixtures.createRandomized();
    createAndSaveForm(rdbmsService, randomizedForm);

    final FormDbModel updatedModel =
        randomizedForm.copy(b -> ((FormDbModelBuilder) b).isDeleted(true));
    rdbmsService.createWriter(1L).getFormWriter().update(updatedModel);

    final var form = rdbmsService.getFormReader().findOne(randomizedForm.formKey()).orElse(null);
    assertFormEntity(form, updatedModel);
  }

  @TestTemplate
  public void shouldFindFormByFormId(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final FormDbModel randomizedForm = FormFixtures.createRandomized();
    createAndSaveForm(rdbmsService, randomizedForm);

    final var searchResult =
        rdbmsService
            .getFormReader()
            .search(
                new FormQuery(
                    new FormFilter.Builder().formIds(randomizedForm.formId()).build(),
                    FormSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(10))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertFormEntity(searchResult.items().getFirst(), randomizedForm);
  }

  @TestTemplate
  public void shouldFindAllFormsPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();

    final String id = FormFixtures.nextStringId();
    createAndSaveRandomForms(rdbmsService, id);

    final var searchResult =
        rdbmsService
            .getFormReader()
            .search(
                new FormQuery(
                    new FormFilter.Builder().formIds(id).build(),
                    FormSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindFormWithFullFilter(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final FormReader formReader = rdbmsService.getFormReader();

    final String id = FormFixtures.nextStringId();
    createAndSaveRandomForms(rdbmsService, id);
    final FormDbModel randomizedForm = FormFixtures.createRandomized(b -> b.formId(id));
    createAndSaveForm(rdbmsService, randomizedForm);

    final var searchResult =
        formReader.search(
            new FormQuery(
                new FormFilter.Builder()
                    .formIds(randomizedForm.formId())
                    .formKeys(randomizedForm.formKey())
                    .build(),
                FormSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().formId()).isEqualTo(randomizedForm.formId());
  }

  private static void assertFormEntity(
      final FormEntity instance, final FormDbModel randomizedForm) {
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(randomizedForm);
  }
}
