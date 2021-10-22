/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import {BADGE_TYPE} from 'modules/constants';

import {instancesStore} from 'modules/stores/instances';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import Badge from 'modules/components/Badge';

import * as Styled from './styled';
import {panelStatesStore} from 'modules/stores/panelStates';

const Header: React.FC = observer(() => {
  return (
    <Styled.FiltersHeader>
      <Badge type={BADGE_TYPE.FILTERS} data-testid="filter-panel-header-badge">
        {instancesStore.state.filteredInstancesCount}
      </Badge>
    </Styled.FiltersHeader>
  );
});

const FiltersPanel: React.FC = observer(({children}) => {
  const {
    state: {isFiltersCollapsed},
    toggleFiltersPanel,
  } = panelStatesStore;

  return (
    <CollapsablePanel
      maxWidth={328}
      label="Filters"
      header={<Header />}
      panelPosition="LEFT"
      isCollapsed={isFiltersCollapsed}
      toggle={toggleFiltersPanel}
      scrollable
    >
      {children}
    </CollapsablePanel>
  );
});

export {FiltersPanel};
