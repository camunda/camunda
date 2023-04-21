/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {useEffect, useState} from 'react';
import {CollapsablePanel as BaseCollapsablePanel} from 'modules/components/Carbon/CollapsablePanel';

type Props = {
  label: string;
  children: React.ReactNode;
};

const CollapsablePanel: React.FC<Props> = ({label, children}) => {
  const {isDecisionsFiltersCollapsed = false} = getStateLocally('panelStates');
  const [panelState, setPanelState] = useState<'expanded' | 'collapsed'>(
    isDecisionsFiltersCollapsed ? 'collapsed' : 'expanded'
  );

  useEffect(() => {
    storeStateLocally(
      {
        isDecisionsFiltersCollapsed: panelState === 'collapsed',
      },
      'panelStates'
    );
  }, [panelState]);

  return (
    <BaseCollapsablePanel
      label={label}
      panelPosition="LEFT"
      maxWidth={320}
      isCollapsed={panelState === 'collapsed'}
      onToggle={() => {
        setPanelState((panelState) => {
          if (panelState === 'collapsed') {
            return 'expanded';
          }

          return 'collapsed';
        });
      }}
    >
      {children}
    </BaseCollapsablePanel>
  );
};

export {CollapsablePanel};
