/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {Container, AddIcon, CancelIcon, WarningIcon} from './styled';
import {Stack} from '@carbon/react';
import {useModificationsByElement} from 'modules/hooks/modifications';
import {hasPendingCancelOrMoveModification} from 'modules/utils/modifications';

type Props = {
  elementId: string;
  isPlaceholder?: boolean;
  endDate: string | null;
  scopeKeyHierarchy: string[];
};

const ModificationIcons: React.FC<Props> = observer(
  ({elementId, isPlaceholder = false, endDate, scopeKeyHierarchy}) => {
    const modificationsByElement = useModificationsByElement();

    const hasCancelModification =
      modificationsByElement[elementId]?.areAllTokensCanceled ||
      scopeKeyHierarchy.some((flowNodeInstanceKey) =>
        hasPendingCancelOrMoveModification({
          flowNodeId: elementId,
          flowNodeInstanceKey,
          modificationsByFlowNode: modificationsByElement,
        }),
      );

    return (
      <Container>
        <>
          {isPlaceholder && (
            <Stack orientation="horizontal" gap={3}>
              <WarningIcon data-testid="warning-icon">
                <title>
                  Ensure to add/edit variables if required, input/output
                  mappings are not executed during modification
                </title>
              </WarningIcon>
              <AddIcon data-testid="add-icon">
                <title>This element instance is planned to be added</title>
              </AddIcon>
            </Stack>
          )}

          {hasCancelModification && !isPlaceholder && endDate === null && (
            <CancelIcon data-testid="cancel-icon">
              <title>This element instance is planned to be canceled</title>
            </CancelIcon>
          )}
        </>
      </Container>
    );
  },
);

export {ModificationIcons};
