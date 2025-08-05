/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FieldContainer, Label} from './styled';
import {observer} from 'mobx-react';
import isNil from 'lodash/isNil';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {Dropdown} from '@carbon/react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';

const TargetVersionField: React.FC = observer(() => {
  const {
    migrationState: {selectedTargetVersion},
    targetProcessVersions,
  } = processesStore;

  return (
    <FieldContainer>
      <Label>Version</Label>
      <Dropdown
        id="targetProcessVersion"
        label="-"
        titleText="Target Version"
        hideLabel
        type="inline"
        onChange={async ({selectedItem}) => {
          if (!isNil(selectedItem)) {
            processInstanceMigrationStore.resetItemMapping();
            processesStore.setSelectedTargetVersion(selectedItem);
          }
        }}
        disabled={
          selectedTargetVersion === null && targetProcessVersions.length === 0
        }
        items={targetProcessVersions}
        size="sm"
        selectedItem={selectedTargetVersion}
      />
    </FieldContainer>
  );
});

export {TargetVersionField};
