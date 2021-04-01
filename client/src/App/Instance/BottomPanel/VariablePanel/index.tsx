/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import Variables from '../Variables';
import {FAILED_PLACEHOLDER, MULTI_SCOPE_PLACEHOLDER} from './constants';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';

import * as Styled from './styled';

const VariablePanel = observer(function VariablePanel() {
  const {processInstanceId} = useInstancePageParams();

  useEffect(() => {
    variablesStore.init(processInstanceId);

    return () => {
      variablesStore.reset();
    };
  }, [processInstanceId]);

  const {
    state: {status},
  } = variablesStore;

  return (
    <Styled.VariablesPanel>
      {status === 'error' ? (
        <StatusMessage variant="error">{FAILED_PLACEHOLDER}</StatusMessage>
      ) : (
        <>
          {flowNodeSelectionStore.areMultipleInstancesSelected ? (
            <StatusMessage variant="default">
              {MULTI_SCOPE_PLACEHOLDER}
            </StatusMessage>
          ) : (
            <Variables />
          )}
        </>
      )}
    </Styled.VariablesPanel>
  );
});

export {VariablePanel};
