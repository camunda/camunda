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
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';
import {useProcessInstanceIncidentsErrorTypes} from 'modules/queries/incidents/useGetIncidentsByProcessInstance';
import {getIncidentErrorName} from 'modules/utils/incidents';

type Props = {
  processInstanceKey: string;
};

const IncidentsFilter: React.FC<Props> = observer(({processInstanceKey}) => {
  if (IS_INCIDENTS_PANEL_V2) {
    return <IncidentsFilterV2 processInstanceKey={processInstanceKey} />;
  }

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

const IncidentsFilterV2: React.FC<Props> = observer(({processInstanceKey}) => {
  const {
    setErrorTypeSelection,
    clearSelection,
    state: {selectedErrorTypes},
  } = incidentsStore;
  const availableErrorTypes =
    useProcessInstanceIncidentsErrorTypes(processInstanceKey);

  return (
    <Layer>
      <Container>
        <Stack orientation="horizontal" gap={5}>
          <MultiSelect
            id="incidents-by-errorType"
            data-testid="incidents-by-errorType"
            items={availableErrorTypes}
            itemToString={(selectedItem) => getIncidentErrorName(selectedItem)}
            label="Filter by Incident Type"
            titleText="Filter by Incident Type"
            hideLabel
            onChange={({selectedItems}) => {
              setErrorTypeSelection(selectedItems);
            }}
            size="sm"
          />
          <Button
            kind="ghost"
            onClick={() => {
              clearSelection();
              tracking.track({
                eventName: 'incident-filters-cleared',
              });
            }}
            disabled={selectedErrorTypes.length === 0}
            title="Reset Filters"
            size="sm"
          >
            Reset Filters
          </Button>
        </Stack>
      </Container>
    </Layer>
  );
});

export {IncidentsFilter};
