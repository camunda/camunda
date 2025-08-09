/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container, Stack} from './styled';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {MultiSelect} from '@carbon/react';
import {useIncidentsElements} from 'modules/hooks/incidents';

const IncidentsFilter: React.FC = observer(() => {
  const {
    errorTypes,
    setFlowNodeSelection,
    setErrorTypeSelection,
    state: {selectedErrorTypes, selectedFlowNodes},
  } = incidentsStore;

  const flowNodes = useIncidentsElements();

  return (
    <Container>
      <Stack orientation="vertical" gap={5}>
        <MultiSelect
          id="incidents-by-incident-type"
          data-testid="incidents-by-incident-type"
          items={errorTypes.map(({id}) => id)}
          itemToString={(selectedItem) =>
            errorTypes.find(({id}) => id === selectedItem)?.name ?? selectedItem
          }
          label="Filter by Incident Type"
          titleText="Filter by Incident Type"
          hideLabel
          selectedItems={selectedErrorTypes}
          onChange={({selectedItems}) => {
            setErrorTypeSelection(selectedItems);
          }}
          size="sm"
        />
        <MultiSelect
          id="incidents-by-flow-node"
          data-testid="incidents-by-flow-node"
          items={flowNodes.map(({id}) => id)}
          itemToString={(selectedItem) =>
            flowNodes.find(({id}) => id === selectedItem)?.name ?? selectedItem
          }
          label="Filter by Flow Node"
          titleText="Filter by Flow Node"
          hideLabel
          selectedItems={selectedFlowNodes}
          onChange={({selectedItems}) => {
            setFlowNodeSelection(selectedItems);
          }}
          size="sm"
        />
      </Stack>
    </Container>
  );
});

export {IncidentsFilter};
