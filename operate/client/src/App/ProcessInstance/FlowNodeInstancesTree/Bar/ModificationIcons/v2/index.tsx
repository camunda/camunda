/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {Container, AddIcon, CancelIcon, WarningIcon} from '../styled';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';
import {Stack} from '@carbon/react';
import {useModificationsByFlowNode} from 'modules/hooks/modifications';
import {hasPendingCancelOrMoveModification} from 'modules/utils/modifications';

type Props = {
  flowNodeInstance: Pick<
    FlowNodeInstance,
    'flowNodeId' | 'isPlaceholder' | 'endDate' | 'treePath'
  >;
};

const ModificationIcons: React.FC<Props> = observer(({flowNodeInstance}) => {
  const modificationsByFlowNode = useModificationsByFlowNode();
  const instanceKeyHierarchy = flowNodeInstance.treePath.split('/');

  const hasCancelModification =
    modificationsStore.modificationsByFlowNode[flowNodeInstance.flowNodeId]
      ?.areAllTokensCanceled ||
    instanceKeyHierarchy.some((instanceKey) =>
      hasPendingCancelOrMoveModification(
        flowNodeInstance.flowNodeId,
        instanceKey,
        modificationsByFlowNode,
      ),
    );

  return (
    <Container>
      <>
        {flowNodeInstance.isPlaceholder && (
          <Stack orientation="horizontal" gap={3}>
            <WarningIcon data-testid="warning-icon">
              <title>
                Ensure to add/edit variables if required, input/output mappings
                are not executed during modification
              </title>
            </WarningIcon>
            <AddIcon data-testid="add-icon">
              <title>This flow node instance is planned to be added</title>
            </AddIcon>
          </Stack>
        )}

        {hasCancelModification &&
          !flowNodeInstance.isPlaceholder &&
          flowNodeInstance.endDate === null && (
            <CancelIcon data-testid="cancel-icon">
              <title>This flow node instance is planned to be canceled</title>
            </CancelIcon>
          )}
      </>
    </Container>
  );
});

export {ModificationIcons};
