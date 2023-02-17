/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import Modal from 'modules/components/Modal';
import {OperationItem} from 'modules/components/OperationItem';
import {OperationItems} from 'modules/components/OperationItems';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {DeleteButton, Description, Td, Th} from './styled';

type Props = {
  decisionName: string;
  decisionVersion: string;
};

const DecisionOperations: React.FC<Props> = ({
  decisionName,
  decisionVersion,
}) => {
  const [isDeleteModalVisible, setIsDeleteModalVisible] =
    useState<boolean>(false);

  return (
    <>
      <OperationItems>
        <OperationItem
          title={`Delete Decision Definition "${decisionName} - Version ${decisionVersion}"`}
          type="DELETE"
          onClick={() => setIsDeleteModalVisible(true)}
        />
      </OperationItems>
      <Modal
        onModalClose={() => setIsDeleteModalVisible(false)}
        isVisible={isDeleteModalVisible}
        size="CUSTOM"
        width="755px"
      >
        <Modal.Header>Delete DRD</Modal.Header>
        <Modal.Body>
          <Description>You are about to delete the following DRD:</Description>
          <table>
            <thead>
              <tr>
                <Th>DRD</Th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <Td>{decisionDefinitionStore.name}</Td>
              </tr>
            </tbody>
          </table>
        </Modal.Body>
        <Modal.Footer>
          <Modal.SecondaryButton
            title="Cancel"
            onClick={() => setIsDeleteModalVisible(false)}
          >
            Cancel
          </Modal.SecondaryButton>
          <DeleteButton
            appearance="danger"
            label="Delete"
            onCmPress={() => {}}
            data-testid="delete-button"
          />
        </Modal.Footer>
      </Modal>
    </>
  );
};

export {DecisionOperations};
