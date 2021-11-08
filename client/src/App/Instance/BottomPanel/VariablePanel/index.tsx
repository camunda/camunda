/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import Variables from '../Variables';
import {variablesStore} from 'modules/stores/variables';
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

  const {displayStatus} = variablesStore;

  return (
    <Styled.VariablesPanel>
      {displayStatus === 'error' ? (
        <StatusMessage variant="error">
          Variables could not be fetched
        </StatusMessage>
      ) : displayStatus === 'multi-instances' ? (
        <StatusMessage variant="default">
          To view the Variables, select a single Flow Node Instance in the
          Instance History.
        </StatusMessage>
      ) : (
        <Variables />
      )}
    </Styled.VariablesPanel>
  );
});

export {VariablePanel};
