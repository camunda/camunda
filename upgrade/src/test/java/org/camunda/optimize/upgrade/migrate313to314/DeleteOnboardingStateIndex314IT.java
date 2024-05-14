/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate313to314;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

public class DeleteOnboardingStateIndex314IT extends AbstractUpgrade314IT {

  @Test
  @SneakyThrows
  public void deleteOnboardingStateIndex() {
    // given pre-upgrade
    List<String> onboardingStateIndexList =
        prefixAwareClient.getAllIndexNames().stream()
            .filter(indexName -> indexName.contains("onboarding-state"))
            .toList();
    assertThat(onboardingStateIndexList).hasSize(1);

    // when
    performUpgrade();

    // then
    List<String> onboardingStateIndexListPostUpgrade =
        prefixAwareClient.getAllIndexNames().stream()
            .filter(indexName -> indexName.contains("onboarding-state"))
            .toList();
    assertThat(onboardingStateIndexListPostUpgrade).isEmpty();
  }
}
