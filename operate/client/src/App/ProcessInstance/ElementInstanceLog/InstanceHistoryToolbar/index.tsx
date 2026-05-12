/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Search, SelectableTag} from '@carbon/react';
import {ArrowsVertical} from '@carbon/react/icons';
import {
  Toolbar,
  ToolbarTopRow,
  ToolbarSearch,
  SortButton,
  ChipRow,
} from './styled';

type StatusFilter = 'all' | 'active' | 'incidents';
type SortOrder = 'earliest' | 'latest';

// Prototype-only: counts and filter state are local. Wiring through to the
// element instances tree (filtering nodes, reordering siblings) is out of
// scope for this mockup.
// TODO: Replace with derived counts and tree filtering once API supports it.
const MOCK_ACTIVE_COUNT = 1;
const MOCK_INCIDENT_COUNT = 1;

const InstanceHistoryToolbar: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [sortOrder, setSortOrder] = useState<SortOrder>('earliest');

  return (
    <Toolbar data-testid="instance-history-toolbar">
      <ToolbarTopRow>
        <ToolbarSearch>
          <Search
            size="sm"
            labelText="Search by name or ID"
            placeholder="Search by name or ID..."
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            onClear={() => setSearchTerm('')}
            data-testid="search-instance-history-input"
          />
        </ToolbarSearch>
        <SortButton
          type="button"
          onClick={() =>
            setSortOrder((current) =>
              current === 'earliest' ? 'latest' : 'earliest',
            )
          }
          data-testid="instance-history-sort"
        >
          <ArrowsVertical size={16} aria-hidden />
          {sortOrder === 'earliest' ? 'Earliest first' : 'Latest first'}
        </SortButton>
      </ToolbarTopRow>
      <ChipRow role="group" aria-label="Status filter">
        <SelectableTag
          size="sm"
          text="All"
          selected={statusFilter === 'all'}
          onClick={() => setStatusFilter('all')}
          data-testid="filter-instance-history-all"
        />
        <SelectableTag
          size="sm"
          text={`Active (${MOCK_ACTIVE_COUNT})`}
          selected={statusFilter === 'active'}
          onClick={() => setStatusFilter('active')}
          data-testid="filter-instance-history-active"
        />
        <SelectableTag
          size="sm"
          text={`Incidents (${MOCK_INCIDENT_COUNT})`}
          selected={statusFilter === 'incidents'}
          onClick={() => setStatusFilter('incidents')}
          data-testid="filter-instance-history-incidents"
        />
      </ChipRow>
    </Toolbar>
  );
};

export {InstanceHistoryToolbar};
