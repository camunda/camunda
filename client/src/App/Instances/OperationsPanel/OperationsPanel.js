/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import PropTypes from 'prop-types';

import {PANEL_POSITION} from 'modules/constants';
import CollapsablePanel from 'modules/components/CollapsablePanel';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import useBatchOperations from './useBatchOperations';

import * as Styled from './styled';
import {isBatchOperationRunning} from './service';
import OperationsEntry from './OperationsEntry';

function OperationsPanel({isOperationsCollapsed, toggleOperations}) {
  const {batchOperations, requestBatchOperations} = useBatchOperations();

  useEffect(requestBatchOperations, []);

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
      <Styled.OperationsList>
        {batchOperations.map(batchOperation => (
          <OperationsEntry
            isRunning={isBatchOperationRunning(batchOperation)}
            id={batchOperation.id}
            type={batchOperation.type}
            key={batchOperation.id}
            data-test="operations-entry"
          />
        ))}
      </Styled.OperationsList>
    </CollapsablePanel>
  );
}

OperationsPanel.propTypes = {
  isOperationsCollapsed: PropTypes.bool.isRequired,
  toggleOperations: PropTypes.func.isRequired,
  dataManager: PropTypes.object
};

export default withCollapsablePanel(OperationsPanel);
