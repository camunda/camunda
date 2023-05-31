/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {Container, AddIcon, CancelIcon, WarningIcon} from './styled';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {Stack} from '@carbon/react';

type Props = {
  flowNodeInstance: Pick<
    FlowNodeInstance,
    'flowNodeId' | 'isPlaceholder' | 'endDate' | 'treePath'
  >;
};

const ModificationIcons: React.FC<Props> = observer(({flowNodeInstance}) => {
  const instanceKeyHierarchy = flowNodeInstance.treePath.split('/');

  const hasCancelModification =
    modificationsStore.modificationsByFlowNode[flowNodeInstance.flowNodeId]
      ?.areAllTokensCanceled ||
    instanceKeyHierarchy.some((instanceKey) =>
      modificationsStore.hasPendingCancelOrMoveModification(
        flowNodeInstance.flowNodeId,
        instanceKey
      )
    );

  return (
    <Container>
      <>
        {flowNodeInstance.isPlaceholder && (
          <Stack orientation="horizontal" gap={3}>
            <WarningIcon>
              <title>
                Ensure to add/edit variables if required, input/output mappings
                are not executed during modification
              </title>
            </WarningIcon>
            <AddIcon>
              <title>This flow node instance is planned to be added</title>
            </AddIcon>
          </Stack>
        )}

        {hasCancelModification &&
          !flowNodeInstance.isPlaceholder &&
          flowNodeInstance.endDate === null && (
            <CancelIcon>
              <title>This flow node instance is planned to be canceled</title>
            </CancelIcon>
          )}
      </>
    </Container>
  );
});

export {ModificationIcons};
