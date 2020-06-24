/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import {useParams} from 'react-router-dom';
import Variables from '../Variables';
import EmptyPanel from 'modules/components/EmptyPanel';
import {FAILED_PLACEHOLDER, MULTI_SCOPE_PLACEHOLDER} from './constants';
import {variables} from 'modules/stores/variables';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {observer} from 'mobx-react';

import * as Styled from './styled';

const VariablePanel = observer(function VariablePanel() {
  const {id: workflowInstanceId} = useParams();

  useEffect(() => {
    variables.init(workflowInstanceId);
    return () => {
      variables.reset();
    };
  }, [workflowInstanceId]);

  const {
    scopeId,
    state: {isFailed},
  } = variables;
  return (
    <Styled.VariablesPanel>
      {isFailed || flowNodeInstance.areMultipleNodesSelected ? (
        <EmptyPanel
          type={isFailed ? 'warning' : 'info'}
          label={isFailed ? FAILED_PLACEHOLDER : MULTI_SCOPE_PLACEHOLDER}
        />
      ) : (
        <Variables key={scopeId ? scopeId : workflowInstanceId} />
      )}
    </Styled.VariablesPanel>
  );
});

export {VariablePanel};
