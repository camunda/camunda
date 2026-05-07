/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ContentSwitcher, Search, Switch} from '@carbon/react';
import {Toolbar, ToolbarSearch, ToolbarTypeFilter} from './styled';

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
      <ToolbarTypeFilter>
        <ContentSwitcher
          size="sm"
          selectedIndex={typeFilter === 'all' ? 0 : 1}
          onChange={({name}) => onTypeFilterChange(name as VariableTypeFilter)}
          data-testid="variable-type-filter"
        >
          <Switch name="all" text="All" />
          <Switch name="documents" text="Documents" />
        </ContentSwitcher>
      </ToolbarTypeFilter>
    </Toolbar>
  );
};

export {VariablesToolbar};
export type {VariableTypeFilter};
