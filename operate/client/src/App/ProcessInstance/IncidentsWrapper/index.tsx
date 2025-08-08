/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useState} from 'react';

import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {IncidentsFilter} from './IncidentsFilter';
import {IncidentsTiles} from './tiles/IncidentsTiles';

import {Filter as FilterIcon} from '@carbon/react/icons';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {getFilteredIncidents, init} from 'modules/utils/incidents';
import {useIncidents} from 'modules/hooks/incidents';
import {CollapsablePanel} from 'modules/components/CollapsablePanel';
import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {FilterRow, ResultsText, FilterButton, ResetButton} from './styled';


type Props = {
  processInstance: ProcessInstance;
};

const IncidentsWrapper: React.FC<Props> = observer(({processInstance}) => {
  const incidents = useIncidents();
  const filteredIncidents = getFilteredIncidents(incidents);
  const [areFiltersVisible, setAreFiltersVisible] = useState(false);
  const hasActiveFilters = 
    incidentsStore.state.selectedErrorTypes.length > 0 || 
    incidentsStore.state.selectedFlowNodes.length > 0;

  useEffect(() => {
    init(processInstance);

    return () => {
      incidentsStore.reset();
    };
  }, [processInstance]);

  if (incidentsStore.incidentsCount === 0) {
    return null;
  }

  return (
    <CollapsablePanel
      label="Incidents"
      panelPosition="RIGHT"
      maxWidth={420}
      isOverlay
      isCollapsed={!incidentsStore.state.isIncidentBarOpen}
      onToggle={() =>
        incidentsStore.setIncidentBarOpen(
          !incidentsStore.state.isIncidentBarOpen,
        )
      }
    >
      <FilterRow>
        <ResultsText>
          {pluralSuffix(filteredIncidents.length, 'result')}
        </ResultsText>
        <div style={{display: 'flex', alignItems: 'center'}}>
          <FilterButton
            type="button"
            onClick={() => setAreFiltersVisible((v) => !v)}
            data-testid="toggle-incidents-filters"
            aria-expanded={areFiltersVisible}
            $isActive={true}
          >
            <FilterIcon size={16} />
            {areFiltersVisible ? 'Hide filters' : 'Filter'}
          </FilterButton>
          {hasActiveFilters && (
            <ResetButton
              type="button"
              onClick={() => incidentsStore.clearSelection()}
              data-testid="reset-incidents-filters"
            >
              Reset filters
            </ResetButton>
          )}
        </div>
      </FilterRow>
      {areFiltersVisible && <IncidentsFilter />}
      <IncidentsTiles />
    </CollapsablePanel>
  );
});
export {IncidentsWrapper};
