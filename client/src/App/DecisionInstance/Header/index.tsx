/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {drdStore} from 'modules/stores/drd';

const Header: React.FC = () => {
  return (
    <div data-testid="decision-instance-header">
      Header{' '}
      {drdStore.state.panelState === 'closed' && (
        <button
          title="Show DRD Panel"
          onClick={() => drdStore.setPanelState('minimized')}
        >
          Show DRD Panel
        </button>
      )}
    </div>
  );
};

export {Header};
