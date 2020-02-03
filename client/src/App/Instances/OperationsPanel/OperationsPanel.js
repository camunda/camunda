/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState, useCallback} from 'react';
import PropTypes from 'prop-types';

import {
  PANEL_POSITION,
  SUBSCRIPTION_TOPIC,
  LOADING_STATE
} from 'modules/constants';
import {withData} from 'modules/DataManager';
import CollapsablePanel from 'modules/components/CollapsablePanel';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';
import useDataManager from 'modules/hooks/useDataManager';

import * as Styled from './styled';
import {isBatchOperationRunning} from './service';
import OperationsEntry from './OperationsEntry';

function OperationsPanel({
  isOperationsCollapsed,
  toggleOperations,
  dataManager
}) {
  const [batchOperations, setBatchOperations] = useState([]);
  const {subscribe, unsubscribe} = useDataManager();

  const handleSubscription = useCallback(() => {
    subscribe(
      SUBSCRIPTION_TOPIC.LOAD_BATCH_OPERATIONS,
      LOADING_STATE.LOADED,
      data => setBatchOperations(data)
    );

    dataManager.getBatchOperations({pageSize: 20});
    return () => unsubscribe();
  }, [subscribe, unsubscribe, dataManager]);

  useEffect(handleSubscription, []);

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

export default withData(withCollapsablePanel(OperationsPanel));
