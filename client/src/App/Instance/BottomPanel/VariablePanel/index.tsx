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
import {variablesStore} from 'modules/stores/variables';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {observer} from 'mobx-react';

import * as Styled from './styled';

const VariablePanel = observer(function VariablePanel() {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'id' does not exist on type '{}'.
  const {id: workflowInstanceId} = useParams();

  useEffect(() => {
    variablesStore.init(workflowInstanceId);

    return () => {
      variablesStore.reset();
    };
  }, [workflowInstanceId]);

  const {
    scopeId,
    state: {isFailed},
  } = variablesStore;
  return (
    <Styled.VariablesPanel>
      {isFailed || flowNodeInstanceStore.areMultipleNodesSelected ? (
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
