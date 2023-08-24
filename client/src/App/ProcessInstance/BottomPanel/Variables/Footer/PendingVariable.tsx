/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    <Layer $hasPendingVariable data-testid={name}>
      <VariableName title={name}>{name}</VariableName>
      <VariableValue>{value}</VariableValue>
      <Operations showLoadingIndicator />
    </Layer>
  );
});

export {PendingVariable};
