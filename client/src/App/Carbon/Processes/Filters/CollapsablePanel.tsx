/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {CollapsablePanel as BaseCollapsablePanel} from 'modules/components/Carbon/CollapsablePanel';
import {panelStatesStore} from 'modules/stores/panelStates';

type Props = {
  children: React.ReactNode;
};

const CollapsablePanel: React.FC<Props> = observer(({children}) => {
  const {
    state: {isFiltersCollapsed},
    toggleFiltersPanel,
  } = panelStatesStore;

  return (
    <BaseCollapsablePanel
      label="Filters"
      panelPosition="LEFT"
      maxWidth={320}
      isCollapsed={isFiltersCollapsed}
      onToggle={toggleFiltersPanel}
    >
      {children}
    </BaseCollapsablePanel>
  );
});

export {CollapsablePanel};
