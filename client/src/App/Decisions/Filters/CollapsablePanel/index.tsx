/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {useEffect, useState} from 'react';
import {CollapsablePanel as CollapsablePanelBase} from './styled';

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
    <CollapsablePanelBase
      maxWidth={328}
      label={label}
      panelPosition="LEFT"
      verticalLabelOffset={27}
      isCollapsed={panelState === 'collapsed'}
      toggle={() => {
        setPanelState((panelState) => {
          if (panelState === 'collapsed') {
            return 'expanded';
          }

          return 'collapsed';
        });
      }}
      scrollable
    >
      {children}
    </CollapsablePanelBase>
  );
};

export {CollapsablePanel};
