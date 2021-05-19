/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Spinner} from '../styled';
import {Container, Name, Value, DisplayText} from './styled';

import {variablesStore} from 'modules/stores/variables';
import {observer} from 'mobx-react';

const PendingVariable: React.FC = observer(() => {
  const {
    state: {pendingItem},
  } = variablesStore;

  if (pendingItem === null) {
    return null;
  }

  return (
    <Container data-testid={pendingItem.name}>
      <Name title={pendingItem.name}>{pendingItem.name}</Name>
      <Value>
        <DisplayText>{pendingItem.value}</DisplayText>
        <Spinner data-testid="edit-variable-spinner" />
      </Value>
    </Container>
  );
});

export {PendingVariable};
