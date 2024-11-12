/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processesStore} from 'modules/stores/processes/processes.migration';
import pluralSuffix from 'modules/utils/pluralSuffix';

const MigrationDetails: React.FC = observer(() => {
  const {
    migrationState: {selectedTargetProcess, selectedTargetVersion},
    getSelectedProcessDetails,
  } = processesStore;

  const {
    processName: selectedSourceProcessName,
    version: selectedSourceProcessVersion,
  } = getSelectedProcessDetails();

  return (
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
  );
});

export {MigrationDetails};
