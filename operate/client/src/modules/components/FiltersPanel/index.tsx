/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {CollapsablePanel} from 'modules/components/CollapsablePanel';
import {Button} from '@carbon/react';
import {Footer} from './styled';

type Props = {
  localStorageKey:
    | 'isFiltersCollapsed'
    | 'isDecisionsFiltersCollapsed'
    | 'isAuditLogsFiltersCollapsed';
  children: React.ReactNode;
  onResetClick?: () => void;
  isResetButtonDisabled: boolean;
};

const FiltersPanel: React.FC<Props> = ({
  children,
  localStorageKey,
  onResetClick,
  isResetButtonDisabled,
}) => {
  const isCollapsed = getStateLocally('panelStates')[localStorageKey] ?? false;
  const [panelState, setPanelState] = useState<'expanded' | 'collapsed'>(
    isCollapsed ? 'collapsed' : 'expanded',
  );

  useEffect(() => {
    storeStateLocally(
      {
        [localStorageKey]: panelState === 'collapsed',
      },
      'panelStates',
    );
  }, [panelState, localStorageKey]);

  return (
    <CollapsablePanel
      label="Filter"
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
      footer={
        <Footer>
          <Button
            kind="ghost"
            size="sm"
            disabled={isResetButtonDisabled}
            type="reset"
            onClick={onResetClick}
          >
            Reset filters
          </Button>
        </Footer>
      }
    >
      {children}
    </CollapsablePanel>
  );
};

export {FiltersPanel};
