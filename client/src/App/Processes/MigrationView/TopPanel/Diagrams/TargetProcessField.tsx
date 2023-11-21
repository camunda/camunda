/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FieldContainer, Label} from './styled';
import {observer} from 'mobx-react';
import {isNil} from 'lodash';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {Dropdown} from '@carbon/react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';

const TargetProcessField: React.FC = observer(() => {
  const {
    versionsByProcessAndTenant,
    processes,
    migrationState: {selectedTargetProcess},
  } = processesStore;

  return (
    <FieldContainer>
      <Label>Target</Label>
      <Dropdown
        id="targetProcess"
        label="Select target process"
        titleText="Target Process"
        hideLabel
        type="inline"
        items={processes.map(({id, label}) => {
          return {
            label,
            id,
          };
        })}
        size="sm"
        onChange={({selectedItem}) => {
          if (isNil(selectedItem)) {
            return;
          }

          processInstanceMigrationStore.resetFlowNodeMapping();

          processesStore.setSelectedTargetProcess(selectedItem.id);

          const versions = versionsByProcessAndTenant[selectedItem.id];

          const initialVersionSelection =
            versions?.[versions.length - 1]?.version ?? null;

          processesStore.setSelectedTargetVersion(initialVersionSelection);

          processInstanceMigrationStore.setTargetProcessDefinitionKey(
            processesStore.selectedTargetProcessId ?? null,
          );
        }}
        selectedItem={
          selectedTargetProcess === null
            ? null
            : {
                label: selectedTargetProcess.name,
                id: selectedTargetProcess.key,
              }
        }
      />
    </FieldContainer>
  );
});

export {TargetProcessField};
