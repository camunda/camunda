/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Search} from '@carbon/react';
import {
  Toolbar,
  ToolbarSearch,
  TypeStrip,
  TypeStripButton,
  TypeStripDivider,
} from './styled';

type VariableTypeFilter = 'all' | 'documents';

type Props = {
  searchTerm: string;
  onSearchChange: (value: string) => void;
  typeFilter: VariableTypeFilter;
  onTypeFilterChange: (value: VariableTypeFilter) => void;
};

const VariablesToolbar: React.FC<Props> = ({
  searchTerm,
  onSearchChange,
  typeFilter,
  onTypeFilterChange,
}) => {
  return (
    <Toolbar data-testid="variables-toolbar">
      <ToolbarSearch>
        <Search
          size="sm"
          labelText="Search variables by name"
          placeholder="Search variables by name"
          value={searchTerm}
          onChange={(event) => onSearchChange(event.target.value)}
          onClear={() => onSearchChange('')}
          data-testid="search-variables-input"
        />
      </ToolbarSearch>
      <TypeStrip role="group" aria-label="Variable type filter">
        <TypeStripButton
          type="button"
          $active={typeFilter === 'all'}
          aria-pressed={typeFilter === 'all'}
          onClick={() => onTypeFilterChange('all')}
          data-testid="filter-variables-all"
        >
          All
        </TypeStripButton>
        <TypeStripDivider aria-hidden>·</TypeStripDivider>
        <TypeStripButton
          type="button"
          $active={typeFilter === 'documents'}
          aria-pressed={typeFilter === 'documents'}
          onClick={() => onTypeFilterChange('documents')}
          data-testid="filter-variables-documents"
        >
          Documents
        </TypeStripButton>
      </TypeStrip>
    </Toolbar>
  );
};

export {VariablesToolbar};
export type {VariableTypeFilter};
