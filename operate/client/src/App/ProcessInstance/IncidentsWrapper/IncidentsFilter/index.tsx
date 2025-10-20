/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container, Stack, Layer} from './styled';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {tracking} from 'modules/tracking';
import {Button, MultiSelect} from '@carbon/react';
import {useIncidentsElements} from 'modules/hooks/incidents';
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';
import {useProcessInstanceIncidentsErrorTypes} from 'modules/queries/incidents/useGetIncidentsByProcessInstance';
import {getIncidentErrorName} from 'modules/utils/incidents';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';

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
    clearSelection,
    state: {selectedErrorTypes, selectedFlowNodes},
  } = incidentsStore;

  const flowNodes = useIncidentsElements();

  return (
    <Layer>
      <Container>
        <Stack orientation="horizontal" gap={5}>
          <MultiSelect
            id="incidents-by-flowNode"
            data-testid="incidents-by-flowNode"
            items={flowNodes.map(({id}) => id)}
            itemToString={(selectedItem) =>
              flowNodes.find(({id}) => id === selectedItem)?.name ??
              selectedItem
            }
            label="Filter by Flow Node"
            titleText="Filter by Flow Node"
            hideLabel
            onChange={({selectedItems}) => {
              setFlowNodeSelection(selectedItems);
            }}
            size="sm"
          />
          <MultiSelect
            id="incidents-by-errorType"
            data-testid="incidents-by-errorType"
            items={errorTypes.map(({id}) => id)}
            itemToString={(selectedItem) =>
              errorTypes.find(({id}) => id === selectedItem)?.name ??
              selectedItem
            }
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
            disabled={
              selectedFlowNodes.length === 0 && selectedErrorTypes.length === 0
            }
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

const IncidentsFilterV2: React.FC<Props> = observer(({processInstanceKey}) => {
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
            selectedItems={incidentsPanelStore.state.selectedErrorTypes}
            itemToString={(selectedItem) => getIncidentErrorName(selectedItem)}
            label="Filter by Incident Type"
            titleText="Filter by Incident Type"
            hideLabel
            onChange={({selectedItems}) => {
              incidentsPanelStore.setErrorTypeSelection(selectedItems);
            }}
            size="sm"
          />
          <Button
            kind="ghost"
            onClick={() => {
              incidentsPanelStore.clearSelection();
              tracking.track({
                eventName: 'incident-filters-cleared',
              });
            }}
            disabled={!incidentsPanelStore.hasActiveFilters}
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
