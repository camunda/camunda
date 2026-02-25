/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FieldContainer, Label} from './styled';
import {useMemo} from 'react';
import {observer} from 'mobx-react';
import {Dropdown} from '@carbon/react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useAvailableMigrationTargetProcessDefinitionVersions} from 'modules/hooks/migrationTargetProcessDefinitions';

const TargetVersionField: React.FC = observer(() => {
  const {targetProcessDefinition} = processInstanceMigrationStore.state;
  const {data: availableDefinitions} =
    useAvailableMigrationTargetProcessDefinitionVersions();

  const selectedVersion = targetProcessDefinition?.version ?? null;
  const versions = useMemo(() => {
    return availableDefinitions?.map((d) => d.version) ?? [];
  }, [availableDefinitions]);

  return (
    <FieldContainer>
      <Label>Version</Label>
      <Dropdown
        id="targetProcessVersion"
        label="-"
        titleText="Target Version"
        hideLabel
        type="inline"
        onChange={({selectedItem}) => {
          const matchingDefinition =
            selectedItem === null
              ? null
              : (availableDefinitions?.find(
                  (d) => d.version === selectedItem,
                ) ?? null);

          processInstanceMigrationStore.resetElementMapping();
          processInstanceMigrationStore.setTargetProcessDefinition(
            matchingDefinition,
          );
        }}
        disabled={selectedVersion === null && versions.length === 0}
        items={versions}
        size="sm"
        selectedItem={selectedVersion}
      />
    </FieldContainer>
  );
});

export {TargetVersionField};
