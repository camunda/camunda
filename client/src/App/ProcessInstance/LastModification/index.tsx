/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, Button, ModificationDetail} from './styled';
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
                payload.targetFlowNode.name
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
