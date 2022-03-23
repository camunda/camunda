/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.importing.permutations;

import com.google.common.collect.Collections2;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.permutations.AbstractImportMediatorPermutationsIT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public abstract class AbstractExtendedImportMediatorPermutationsIT extends AbstractImportMediatorPermutationsIT {
  private static final int MAX_MEDIATORS_TO_PERMUTATE = 8;

  protected void logMediatorOrder(List<Class<? extends ImportMediator>> mediatorOrder) {
    StringBuilder order = new StringBuilder();
    mediatorOrder.forEach(mediator -> order.append(mediator.getSimpleName()).append("\n"));

    log.warn("Testing the following mediators order: \n" + order);
  }

  protected static Stream<List<Class<? extends ImportMediator>>> getMediatorPermutationsStream(
    final List<Class<? extends ImportMediator>> requiredMediatorClasses,
    List<Class<? extends ImportMediator>> additionalMediatorClasses) {

    Collections.shuffle(additionalMediatorClasses);
    additionalMediatorClasses = additionalMediatorClasses.subList(0, MAX_MEDIATORS_TO_PERMUTATE - requiredMediatorClasses.size());

    List<Class<? extends ImportMediator>> importMediatorsToUse = new ArrayList<>(requiredMediatorClasses);
    importMediatorsToUse.addAll(additionalMediatorClasses);

    return Collections2.permutations(importMediatorsToUse).stream();
  }
}
