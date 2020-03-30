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
import * as CONSTANTS from './constants';
import {hasBatchOperations} from './service';
import OperationsEntry from './OperationsEntry';

function OperationsPanel({
  isOperationsCollapsed,
  toggleOperations,
  onInstancesClick
}) {
  const {
    batchOperations,
    requestBatchOperations,
    requestNextBatchOperations
  } = useBatchOperations();

  useEffect(requestBatchOperations, []);

  return (
    <CollapsablePanel
      label={CONSTANTS.OPERATIONS_LABEL}
      panelPosition={PANEL_POSITION.RIGHT}
      maxWidth={478}
      isOverlay
      isCollapsed={isOperationsCollapsed}
      toggle={toggleOperations}
      hasBackgroundColor
      verticalLabelOffset={27}
      onScroll={event => {
        const {target} = event;

        if (
          target.scrollHeight - target.clientHeight - target.scrollTop <= 0 &&
          batchOperations.length > 0
        ) {
          requestNextBatchOperations(
            batchOperations[batchOperations.length - 1].sortValues
          );
        }
      }}
    >
      <Styled.OperationsList>
        {hasBatchOperations(batchOperations) ? (
          batchOperations.map(batchOperation => (
            <OperationsEntry
              onInstancesClick={onInstancesClick}
              key={batchOperation.id}
              batchOperation={batchOperation}
              data-test="operations-entry"
            />
          ))
        ) : (
          <Styled.EmptyMessage>{CONSTANTS.EMPTY_MESSAGE}</Styled.EmptyMessage>
        )}
      </Styled.OperationsList>
    </CollapsablePanel>
  );
}

OperationsPanel.propTypes = {
  isOperationsCollapsed: PropTypes.bool.isRequired,
  toggleOperations: PropTypes.func.isRequired,
  dataManager: PropTypes.object,
  onInstancesClick: PropTypes.func.isRequired
};

export default withCollapsablePanel(OperationsPanel);
