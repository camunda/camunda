/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import {useParams} from 'react-router-dom';
import Variables from '../Variables';
import {FAILED_PLACEHOLDER, MULTI_SCOPE_PLACEHOLDER} from './constants';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';

import * as Styled from './styled';

const VariablePanel = observer(function VariablePanel() {
  const {id: workflowInstanceId} = useParams<{id: string}>();

  useEffect(() => {
    variablesStore.init(workflowInstanceId);

    return () => {
      variablesStore.reset();
    };
  }, [workflowInstanceId]);

  const {
    scopeId,
    state: {status},
  } = variablesStore;

  return (
    <Styled.VariablesPanel>
      {status === 'error' ? (
        <StatusMessage variant="error">{FAILED_PLACEHOLDER}</StatusMessage>
      ) : (
        <>
          {/* @ts-expect-error */}
          {flowNodeInstanceStore.areMultipleNodesSelected ? (
            <StatusMessage variant="default">
              {MULTI_SCOPE_PLACEHOLDER}
            </StatusMessage>
          ) : (
            <Variables key={scopeId ?? workflowInstanceId} />
          )}
        </>
      )}
    </Styled.VariablesPanel>
  );
});

export {VariablePanel};
