/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Spinner} from '../styled';
import {Container, Name, Value, DisplayText, SpinnerContainer} from './styled';

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
      </Value>
      <SpinnerContainer>
        <Spinner data-testid="edit-variable-spinner" />
      </SpinnerContainer>
    </Container>
  );
});

export {PendingVariable};
