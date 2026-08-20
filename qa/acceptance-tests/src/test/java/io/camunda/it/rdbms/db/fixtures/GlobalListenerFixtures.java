/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel.GlobalListenerDbModelBuilder;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class GlobalListenerFixtures extends CommonFixtures {

  private GlobalListenerFixtures() {}

  public static GlobalListenerDbModel createRandomGlobalListener(
      final Function<GlobalListenerDbModelBuilder, GlobalListenerDbModelBuilder> builderFunction) {
    final var key = CommonFixtures.nextStringKey();
    final var builder =
        new GlobalListenerDbModelBuilder()
            .listenerId("listener-" + key)
            .type("job-" + key)
            .retries(RANDOM.nextInt(1, 5))
            .eventTypes(List.of("creating", "assigning"))
            .afterNonGlobal(false)
            .priority(RANDOM.nextInt(0, 100))
            .source(GlobalListenerSource.CONFIGURATION)
            .listenerType(GlobalListenerType.USER_TASK);
    return builderFunction.apply(builder).build();
  }

  public static List<GlobalListenerDbModel> createAndSaveRandomGlobalListeners(
      final CamundaRdbmsTestApplication testApplication,
      final int numberOfListeners,
      final Function<GlobalListenerDbModelBuilder, GlobalListenerDbModelBuilder> builderFunction) {
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    final List<GlobalListenerDbModel> models = new ArrayList<>();
    for (int i = 0; i < numberOfListeners; i++) {
      final GlobalListenerDbModel randomized = createRandomGlobalListener(builderFunction);
      models.add(randomized);
      rdbmsWriters.getGlobalListenerWriter().create(randomized);
    }
    rdbmsWriters.flush();
    return models;
  }

  public static GlobalListenerDbModel createAndSaveRandomGlobalListener(
      final CamundaRdbmsTestApplication testApplication,
      final Function<GlobalListenerDbModelBuilder, GlobalListenerDbModelBuilder> builderFunction) {
    return createAndSaveRandomGlobalListeners(testApplication, 1, builderFunction).getFirst();
  }
}
