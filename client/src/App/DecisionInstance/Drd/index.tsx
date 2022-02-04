/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {drdStore} from 'modules/stores/drd';

const Drd: React.FC = () => {
  const {
    setPanelState,
    state: {panelState},
  } = drdStore;

  return (
    <div data-testid="drd">
      <div>
        DrdPanel
        {panelState === 'minimized' && (
          <button
            title="Maximize DRD Panel"
            onClick={() => setPanelState('maximized')}
          >
            Maximize
          </button>
        )}
        {panelState === 'maximized' && (
          <button
            title="Minimize DRD Panel"
            onClick={() => setPanelState('minimized')}
          >
            Minimize
          </button>
        )}
        <button title="Close DRD Panel" onClick={() => setPanelState('closed')}>
          X
        </button>
      </div>
    </div>
  );
};

export {Drd};
