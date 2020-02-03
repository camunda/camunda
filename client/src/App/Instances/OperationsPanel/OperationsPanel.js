/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {PANEL_POSITION} from 'modules/constants';
import CollapsablePanel from 'modules/components/CollapsablePanel';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';

import {OPERATION_TYPES} from './constants';
import OperationsEntry from './OperationsEntry';

// comment out to see data in operations panel
// TODO (paddy): will be replaced by real data in OPE-846
//
const batchOperations = [];
// const batchOperations = [
//   {
//     type: OPERATION_TYPES.RETRY,
//     id: 123456789,
//     isRunning: true
//   },
//   {
//     type: OPERATION_TYPES.CANCEL,
//     id: 987654321,
//     isRunning: false
//   }
// ];

function OperationsPanel({isOperationsCollapsed, toggleOperations}) {
  return (
    <CollapsablePanel
      label="Operations"
      panelPosition={PANEL_POSITION.RIGHT}
      maxWidth={478}
      isOverlay
      isCollapsed={isOperationsCollapsed}
      toggle={toggleOperations}
      verticalLabelOffset={27}
    >
      <div>
        {batchOperations.map(batchOperation => (
          <OperationsEntry {...batchOperation} key={batchOperation.id} />
        ))}
      </div>
    </CollapsablePanel>
  );
}

OperationsPanel.propTypes = {
  isOperationsCollapsed: PropTypes.bool.isRequired,
  toggleOperations: PropTypes.func.isRequired
};

export default withCollapsablePanel(OperationsPanel);
