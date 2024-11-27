/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.form;

import static io.camunda.it.rdbms.db.fixtures.FormFixtures.createAndSaveRandomForms;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.FormReader;
import io.camunda.it.rdbms.db.fixtures.FormFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.filter.FormFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.FormQuery;
import io.camunda.search.sort.FormSort;
import java.util.Comparator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class FormSortIT {

  @TestTemplate
  public void shouldSortFormsByVersionAsc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final FormReader formReader = rdbmsService.getFormReader();

    final String id = FormFixtures.nextStringId();
    createAndSaveRandomForms(rdbmsService, id);

    final var searchResult =
        formReader.search(
            FormQuery.of(
                b ->
                    b.filter(new FormFilter.Builder().formIds(id).build())
                        .sort(FormSort.of(s -> s.version().asc()))
                        .page(SearchQueryPage.of(p -> p.from(0).size(10)))));

    assertThat(searchResult.items()).isSortedAccordingTo(Comparator.comparing(FormEntity::version));
  }

  @TestTemplate
  public void shouldSortFormsByVersionDesc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final FormReader formReader = rdbmsService.getFormReader();

    final String id = FormFixtures.nextStringId();
    createAndSaveRandomForms(rdbmsService, id);

    final var searchResult =
        formReader.search(
            FormQuery.of(
                b ->
                    b.filter(new FormFilter.Builder().formIds(id).build())
                        .sort(FormSort.of(s -> s.version().desc()))
                        .page(SearchQueryPage.of(p -> p.from(0).size(10)))));

    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(FormEntity::version).reversed());
  }
}
