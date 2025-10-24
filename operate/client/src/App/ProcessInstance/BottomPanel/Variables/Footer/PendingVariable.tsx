/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer} from './styled';
import {VariableName, VariableValue} from '../styled';

import {variablesStore} from 'modules/stores/variables';
import {observer} from 'mobx-react';
import {Operations} from '../Operations';

const PendingVariable: React.FC = observer(() => {
  const {
    state: {pendingItem},
  } = variablesStore;

  if (pendingItem === null) {
    return null;
  }

  const {name, value} = pendingItem;

  return (
    <Layer $hasPendingVariable data-testid={`pending-variable-${name}`}>
      <VariableName title={name}>{name}</VariableName>
      <VariableValue>{value}</VariableValue>
      <Operations showLoadingIndicator />
    </Layer>
  );
});

export {PendingVariable};
