/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, Button, ModificationDetail} from './styled';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';

const TOKEN_TEMPLATES = {
  add: (flowNode: string) => `Add "${flowNode}"`,
  cancel: (flowNode: string) => `Cancel "${flowNode}"`,
  move: (sourceFlowNode: string, targetFlowNode: string) =>
    `Move "${sourceFlowNode}" to "${targetFlowNode}"`,
};

const VARIABLE_TEMPLATES = {
  add: (variableName: string) => `Add new variable "${variableName}"`,
  edit: (variableName: string) => `Edit variable "${variableName}"`,
};

const LastModification: React.FC = observer(() => {
  const {lastModification} = modificationsStore;

  if (lastModification === undefined) {
    return null;
  }

  const {type, modification} = lastModification;
  return (
    <Container>
      <div>
        Last added modification:{' '}
        <ModificationDetail>
          <>
            {type === 'token' &&
              modification.operation === 'move' &&
              TOKEN_TEMPLATES[modification.operation](
                modification.flowNode.name,
                modification.targetFlowNode.name
              )}
            {type === 'token' &&
              (modification.operation === 'add' ||
                modification.operation === 'cancel') &&
              TOKEN_TEMPLATES[modification.operation](
                modification.flowNode.name
              )}
            {type === 'variable' &&
              VARIABLE_TEMPLATES[modification.operation](modification.name)}
          </>
        </ModificationDetail>
      </div>
      <Button
        onClick={() => {
          modificationsStore.removeLastModification();
        }}
      >
        Undo
      </Button>
    </Container>
  );
});

export {LastModification};
