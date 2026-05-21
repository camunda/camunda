/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container, Stack, Layer} from './styled';
import {observer} from 'mobx-react';
import {tracking} from 'modules/tracking';
import {Button, MultiSelect} from '@carbon/react';
import {
  availableErrorTypes,
  getIncidentErrorName,
} from 'modules/utils/incidents';
import {incidentsPanelFiltersStore} from 'modules/stores/incidentsPanelFiltersStore';

const IncidentsFilter: React.FC = observer(() => {
  return (
    <Layer>
      <Container>
        <Stack orientation="horizontal" gap={5}>
          <MultiSelect
            id="incidents-by-errorType"
            data-testid="incidents-by-errorType"
            items={availableErrorTypes}
            selectedItems={incidentsPanelFiltersStore.state.selectedErrorTypes}
            itemToString={(selectedItem) => getIncidentErrorName(selectedItem)}
            label="Filter by Incident Type"
            titleText="Filter by Incident Type"
            hideLabel
            onChange={({selectedItems}) => {
              incidentsPanelFiltersStore.setErrorTypeSelection(
                selectedItems ?? [],
              );
            }}
            size="sm"
          />
          <Button
            kind="ghost"
            onClick={() => {
              incidentsPanelFiltersStore.clearSelection();
              tracking.track({
                eventName: 'incident-filters-cleared',
              });
            }}
            disabled={!incidentsPanelFiltersStore.hasActiveFilters}
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
