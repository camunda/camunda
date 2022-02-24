/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState} from 'react';
import {CollapsedPanel, Content, ExpandedPanel, Header} from './styled';

type Props = {
  header: React.ReactNode;
  children: React.ReactNode;
};

const CollapsablePanel: React.FC<Props> = ({header, children}) => {
  const [panelState, setPanelState] = useState<'expanded' | 'collapsed'>(
    'expanded'
  );

  return (
    <>
      {panelState === 'collapsed' && (
        <CollapsedPanel
          onClick={() => {
            setPanelState('expanded');
          }}
        >
          {header}
        </CollapsedPanel>
      )}
      {panelState === 'expanded' && (
        <ExpandedPanel>
          <Header>
            {header}
            <button
              type="button"
              onClick={() => {
                setPanelState('collapsed');
              }}
            >
              Collapse
            </button>
          </Header>
          <Content>{children}</Content>
        </ExpandedPanel>
      )}
    </>
  );
};

export {CollapsablePanel};
