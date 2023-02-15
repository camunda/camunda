/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Modal from 'modules/components/Modal';
import {DeleteButton, Description} from './styled';

type Props = {
  isVisible: boolean;
  title: string;
  description: string;
  bodyContent: React.ReactNode;
  // warningContent: React.ReactNode; TODO: https://github.com/camunda/operate/issues/4020
  onClose: () => void;
  onDelete: () => void;
};

const DeleteDefinitionModal: React.FC<Props> = ({
  isVisible,
  title,
  description,
  bodyContent,
  // warningContent, TODO: https://github.com/camunda/operate/issues/4020
  onClose,
  onDelete,
}) => {
  return (
    <Modal
      onModalClose={onClose}
      isVisible={isVisible}
      size="CUSTOM"
      width="755px"
    >
      <Modal.Header>{title}</Modal.Header>
      <Modal.Body>
        <Description>{description}</Description>
        {bodyContent}
        {/* <WarningContainer content={warningContent} />  TODO: https://github.com/camunda/operate/issues/4020 */}
        {/* [Checkbox for confirmation] TODO: https://github.com/camunda/operate/issues/4025  */}
      </Modal.Body>
      <Modal.Footer>
        <Modal.SecondaryButton title="Cancel" onClick={onClose}>
          Cancel
        </Modal.SecondaryButton>
        <DeleteButton
          appearance="danger"
          label="Delete"
          onCmPress={onDelete}
          data-testid="delete-button"
        />
      </Modal.Footer>
    </Modal>
  );
};

export {DeleteDefinitionModal};
