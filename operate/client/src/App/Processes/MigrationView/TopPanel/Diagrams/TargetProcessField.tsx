/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Label} from './styled';
import {observer} from 'mobx-react';
import isNil from 'lodash/isNil';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {Stack} from '@carbon/react';
import {ComboBox} from 'modules/components/ComboBox';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useMemo} from 'react';

const TargetProcessField: React.FC = observer(() => {
  const {
    versionsByProcessAndTenant,
    processes,
    migrationState: {selectedTargetProcess},
  } = processesStore;

  const items = useMemo(() => {
    return processes.map(({id, label}) => {
      return {
        label,
        id,
      };
    });
  }, [processes]);

  return (
    <Stack orientation="horizontal" gap={5}>
      <Label htmlFor="targetProcess">Target</Label>
      <ComboBox
        aria-label="Target"
        title="Target"
        id="targetProcess"
        placeholder="Search by process name"
        items={items}
        onChange={({selectedItem}) => {
          processInstanceMigrationStore.resetElementMapping();

          if (isNil(selectedItem)) {
            processInstanceMigrationStore.setTargetProcessDefinitionKey(null);
            processesStore.clearSelectedTarget();
            return;
          }

          processesStore.setSelectedTargetProcess(selectedItem.id);

          const versions = versionsByProcessAndTenant[selectedItem.id];

          const initialVersionSelection =
            versions?.[versions.length - 1]?.version ?? null;

          processesStore.setSelectedTargetVersion(initialVersionSelection);

          processInstanceMigrationStore.setTargetProcessDefinitionKey(
            processesStore.selectedTargetProcessId ?? null,
          );
        }}
        value={selectedTargetProcess?.key ?? ''}
      />
    </Stack>
  );
});

export {TargetProcessField};
