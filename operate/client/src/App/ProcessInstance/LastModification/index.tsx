/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Container, ModificationDetail} from './styled';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {tracking} from 'modules/tracking';

const TOKEN_TEMPLATES = {
  ADD_TOKEN: (flowNode: string) => `Add "${flowNode}"`,
  CANCEL_TOKEN: (flowNode: string) => `Cancel "${flowNode}"`,
  MOVE_TOKEN: (sourceFlowNode: string, targetFlowNode: string) =>
    `Move "${sourceFlowNode}" to "${targetFlowNode}"`,
};

const VARIABLE_TEMPLATES = {
  ADD_VARIABLE: (variableName: string) => `Add new variable "${variableName}"`,
  EDIT_VARIABLE: (variableName: string) => `Edit variable "${variableName}"`,
};

const LastModification: React.FC = observer(() => {
  const {lastModification} = modificationsStore;

  if (lastModification === undefined) {
    return null;
  }

  const {type, payload} = lastModification;
  return (
    <Container>
      <div>
        Last added modification:{' '}
        <ModificationDetail>
          <>
            {type === 'token' &&
              payload.operation === 'MOVE_TOKEN' &&
              TOKEN_TEMPLATES[payload.operation](
                payload.flowNode.name,
                payload.targetFlowNode.name,
              )}
            {type === 'token' &&
              (payload.operation === 'ADD_TOKEN' ||
                payload.operation === 'CANCEL_TOKEN') &&
              TOKEN_TEMPLATES[payload.operation](payload.flowNode.name)}
            {type === 'variable' &&
              VARIABLE_TEMPLATES[payload.operation](payload.name)}
          </>
        </ModificationDetail>
      </div>
      <Button
        kind="secondary"
        size="sm"
        onClick={() => {
          tracking.track({
            eventName: 'undo-modification',
            modificationType: lastModification.payload.operation,
          });

          modificationsStore.removeLastModification();
        }}
      >
        Undo
      </Button>
    </Container>
  );
});

export {LastModification};
