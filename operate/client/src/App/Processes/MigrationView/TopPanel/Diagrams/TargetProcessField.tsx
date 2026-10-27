/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {Label} from './styled';
import {observer} from 'mobx-react';
import {Stack} from '@carbon/react';
import {ComboBox} from 'modules/components/ComboBox';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useMemo} from 'react';
import {useAvailableMigrationTargetProcessDefinitions} from 'modules/hooks/migrationTargetProcessDefinitions';
import {getProcessDefinitionName} from 'modules/hooks/processDefinitions';

const TargetProcessField: React.FC = observer(() => {
  const availableDefinitions = useAvailableMigrationTargetProcessDefinitions();
  const {targetProcessDefinition} = processInstanceMigrationStore.state;

  const selectedKey = targetProcessDefinition?.processDefinitionKey;
  const items = useMemo(() => {
    return availableDefinitions.map((d) => ({
      label: getProcessDefinitionName(d),
      id: d.processDefinitionKey,
    }));
  }, [availableDefinitions]);

  return (
    <Stack orientation="horizontal" gap={5}>
      <Label htmlFor="targetProcess">Target</Label>
      <ComboBox
        aria-label="Target"
        title="Target"
        id="targetProcess"
        placeholder="Search by process name"
        items={items}
        value={selectedKey}
        onChange={({selectedItem}) => {
          if (selectedItem === undefined) {
            return;
          }

          let matchingDefinition: ProcessDefinition | undefined;
          if (selectedItem) {
            matchingDefinition = availableDefinitions.find(
              (d) => d.processDefinitionKey === selectedItem.id,
            );
          }

          processInstanceMigrationStore.resetElementMapping();
          processInstanceMigrationStore.setTargetProcessDefinition(
            matchingDefinition ?? null,
          );
        }}
      />
    </Stack>
  );
});

export {TargetProcessField};
