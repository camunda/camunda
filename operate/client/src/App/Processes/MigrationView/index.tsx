/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {InstancesList} from 'App/Layout/InstancesList';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {TopPanel} from './TopPanel';
import {BottomPanel} from './BottomPanel';
import {Footer} from './Footer';
import {Footer as FooterV2} from './Footer/v2';
import {PAGE_TITLE} from 'modules/constants';
import {MigrationSummaryNotification} from './MigrationSummaryNotification';
import {observer} from 'mobx-react';
import {IS_MIGRATION_BATCH_OPERATION_V2} from 'modules/feature-flags';

const MigrationView: React.FC = observer(() => {
  useEffect(() => {
    document.title = PAGE_TITLE.INSTANCES;
  }, []);

  useEffect(() => {
    processesStore.init();
    processesStore.fetchProcesses();

    return () => {
      processesStore.reset();
    };
  }, []);

  const {currentStep} = processInstanceMigrationStore;

  return (
    <>
      <VisuallyHiddenH1>
        Operate Process Instances - Migration Mode
      </VisuallyHiddenH1>

      <InstancesList
        frame={{
          headerTitle: `Migration step ${currentStep?.stepNumber} - ${currentStep?.stepDescription}`,
        }}
        type="migrate"
        topPanel={<TopPanel />}
        bottomPanel={<BottomPanel />}
        footer={IS_MIGRATION_BATCH_OPERATION_V2 ? <FooterV2 /> : <Footer />}
        additionalTopContent={
          processInstanceMigrationStore.isSummaryStep ? (
            <MigrationSummaryNotification />
          ) : undefined
        }
      />
    </>
  );
});

export {MigrationView};
