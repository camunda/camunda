/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {DangerButton} from 'modules/components/Carbon/OperationItem/DangerButton';
import {OperationItems} from 'modules/components/Carbon/OperationItems';
import {DeleteButtonContainer} from 'modules/components/DeleteDefinition/styled';
import {InlineLoading} from '@carbon/react';

type Props = {
  decisionDefinitionId: string;
  decisionName: string;
  decisionVersion: string;
};

const DecisionOperations: React.FC<Props> = ({
  decisionName,
  decisionVersion,
}) => {
  /* eslint-disable */
  const [isDeleteModalVisible, setIsDeleteModalVisible] =
    useState<boolean>(false);

  /* eslint-disable */
  const [isOperationRunning, setIsOperationRunning] = useState(false);

  return (
    <>
      <DeleteButtonContainer>
        {isOperationRunning && (
          <InlineLoading data-testid="delete-operation-spinner" />
        )}
        <OperationItems>
          <DangerButton
            title={`Delete Decision Definition "${decisionName} - Version ${decisionVersion}"`}
            type="DELETE"
            disabled={isOperationRunning}
            onClick={() => setIsDeleteModalVisible(true)}
          />
        </OperationItems>
      </DeleteButtonContainer>
    </>
  );
};

export {DecisionOperations};
