/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Modal, {SIZES} from 'modules/components/Modal';
import * as Styled from './styled';
import {CmButton} from '@camunda-cloud/common-ui-react';

type Props = {
  onModalClose: () => void;
  isVisible: boolean;
  onDeleteClick: React.ComponentProps<typeof CmButton>['onCmPress'];
  instanceId: string;
};

const DeleteOperationModal: React.FC<Props> = ({
  onModalClose,
  isVisible,
  onDeleteClick,
  instanceId,
}) => {
  return (
    <Modal onModalClose={onModalClose} isVisible={isVisible} size={SIZES.SMALL}>
      <Modal.Header>Delete Instance</Modal.Header>
      <Modal.Body>
        <Styled.BodyText>
          <div>About to delete Instance {instanceId}.</div>
          <div>Click "Delete" to proceed.</div>
        </Styled.BodyText>
      </Modal.Body>
      <Modal.Footer>
        <Styled.SecondaryButton title="Cancel" onClick={onModalClose}>
          Cancel
        </Styled.SecondaryButton>
        <CmButton
          appearance="danger"
          label="Delete"
          onCmPress={onDeleteClick}
          data-testid="delete-button"
        />
      </Modal.Footer>
    </Modal>
  );
};

export {DeleteOperationModal};
