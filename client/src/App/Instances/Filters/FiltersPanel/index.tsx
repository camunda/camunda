/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';

import CollapsablePanel from 'modules/components/CollapsablePanel';
import {panelStatesStore} from 'modules/stores/panelStates';

const FiltersPanel: React.FC = observer(({children}) => {
  const {
    state: {isFiltersCollapsed},
    toggleFiltersPanel,
  } = panelStatesStore;

  return (
    <CollapsablePanel
      maxWidth={328}
      label="Filters"
      panelPosition="LEFT"
      verticalLabelOffset={27}
      isCollapsed={isFiltersCollapsed}
      toggle={toggleFiltersPanel}
      scrollable
    >
      {children}
    </CollapsablePanel>
  );
});

export {FiltersPanel};
