/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MigrationSummary, InlineNotification} from './styled';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import pluralSuffix from 'modules/utils/pluralSuffix';

const MigrationSummaryNotification: React.FC = observer(() => {
  const {
    migrationState: {selectedTargetProcess, selectedTargetVersion},
    getSelectedProcessDetails,
  } = processesStore;

  const {
    processName: selectedSourceProcessName,
    version: selectedSourceProcessVersion,
  } = getSelectedProcessDetails();

  return (
    <InlineNotification
      kind="info"
      title=""
      children={
        <MigrationSummary orientation="vertical" gap={5}>
          <p>
            You are about to migrate{' '}
            {pluralSuffix(
              processInstanceMigrationStore.state.selectedInstancesCount,
              'process instance',
            )}{' '}
            from the process definition:{' '}
            <strong>
              {`${selectedSourceProcessName} - version ${selectedSourceProcessVersion}`}
            </strong>{' '}
            to the process definition:{' '}
            <strong>
              {`${selectedTargetProcess?.name} - version ${selectedTargetVersion}`}
            </strong>
          </p>

          <p>
            This process can take several minutes until it completes. You can
            observe progress of this in the operations panel.
          </p>
          <p>
            The flow nodes listed below will be mapped from the source on the
            left side to target on the right side.
          </p>
        </MigrationSummary>
      }
      lowContrast
      hideCloseButton
    />
  );
});

export {MigrationSummaryNotification};
