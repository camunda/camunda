/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, Stack} from './styled';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {tracking} from 'modules/tracking';
import {Layer, Button, MultiSelect} from '@carbon/react';

const IncidentsFilter: React.FC = observer(() => {
  const {
    flowNodes,
    errorTypes,
    setFlowNodeSelection,
    setErrorTypeSelection,
    clearSelection,
    state: {selectedErrorTypes, selectedFlowNodes},
  } = incidentsStore;

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
            selectedItems={selectedFlowNodes}
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
            selectedItems={selectedErrorTypes}
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

export {IncidentsFilter};
