/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {pluralSuffix} from 'modules/utils/pluralSuffix';
import {getProcessDefinitionName} from 'modules/hooks/processDefinitions';

const MigrationDetails: React.FC = observer(() => {
  const {
    sourceProcessDefinition,
    targetProcessDefinition,
    selectedInstancesCount,
  } = processInstanceMigrationStore.state;

  const sourceVersion = sourceProcessDefinition?.version;
  const sourceName = sourceProcessDefinition
    ? getProcessDefinitionName(sourceProcessDefinition)
    : 'Process';

  const targetVersion = targetProcessDefinition?.version;
  const targetName = targetProcessDefinition
    ? getProcessDefinitionName(targetProcessDefinition)
    : 'Process';

  return (
    <p>
      You are about to migrate{' '}
      {pluralSuffix(selectedInstancesCount, 'process instance')} from the
      process definition:{' '}
      <strong>{`${sourceName} - version ${sourceVersion}`}</strong> to the
      process definition:{' '}
      <strong>{`${targetName} - version ${targetVersion}`}</strong>
    </p>
  );
});

export {MigrationDetails};
