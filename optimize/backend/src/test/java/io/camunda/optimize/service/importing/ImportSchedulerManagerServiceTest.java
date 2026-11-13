/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.zeebe.mediator.factory.AbstractZeebeImportMediatorFactory;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;

/**
 * Agentic Phase 2 imports are switched off by having their factories report themselves disabled.
 * This asserts the scheduler honours that, so a disabled import never gets a mediator to run.
 */
class ImportSchedulerManagerServiceTest {

  @Test
  void shouldNotScheduleMediatorsOfDisabledZeebeFactories() {
    // given one enabled and one disabled Zeebe import
    final ImportMediator enabledMediator = mediator();
    final ImportMediator disabledMediator = mediator();
    final List<AbstractZeebeImportMediatorFactory> factories =
        List.of(new FixedFactory(true, enabledMediator), new FixedFactory(false, disabledMediator));

    // when the schedulers are built
    final ImportSchedulerManagerService service =
        new ImportSchedulerManagerService(
            mock(ImportIndexHandlerRegistry.class),
            mock(BeanFactory.class),
            zeebeEnabledConfiguration(),
            List.of(),
            factories);

    // then only the enabled import has a mediator
    assertThat(service.getZeebeImportScheduler())
        .get()
        .satisfies(
            scheduler ->
                assertThat(scheduler.getImportMediators()).containsExactly(enabledMediator));
  }

  private static ConfigurationService zeebeEnabledConfiguration() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    configurationService.getConfiguredZeebe().setEnabled(true);
    configurationService.getConfiguredZeebe().setPartitionCount(1);
    return configurationService;
  }

  private static ImportMediator mediator() {
    final ImportMediator mediator = mock(ImportMediator.class);
    when(mediator.getRank()).thenReturn(MediatorRank.INSTANCE);
    return mediator;
  }

  private static final class FixedFactory extends AbstractZeebeImportMediatorFactory {

    private final boolean enabled;
    private final ImportMediator mediator;

    private FixedFactory(final boolean enabled, final ImportMediator mediator) {
      super(null, null, null, null, null);
      this.enabled = enabled;
      this.mediator = mediator;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    @Override
    public List<ImportMediator> createMediators(final ZeebeDataSourceDto dataSourceDto) {
      return List.of(mediator);
    }
  }
}
