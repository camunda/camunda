/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Modal from 'modules/components/Modal';
import {useRef} from 'react';
import {ModalBody, DeleteButton, Description, CmCheckbox} from './styled';

type Props = {
  isVisible: boolean;
  title: string;
  description: string;
  bodyContent: React.ReactNode;
  confirmationText: string;
  // warningContent: React.ReactNode; TODO: https://github.com/camunda/operate/issues/4020
  onClose: () => void;
  onDelete: () => void;
};

const DeleteDefinitionModal: React.FC<Props> = ({
  isVisible,
  title,
  description,
  bodyContent,
  confirmationText,
  // warningContent, TODO: https://github.com/camunda/operate/issues/4020
  onClose,
  onDelete,
}) => {
  const fieldRef = useRef<HTMLCmCheckboxElement | null>(null);

  return (
    <Modal
      onModalClose={onClose}
      isVisible={isVisible}
      size="CUSTOM"
      width="755px"
    >
      <Modal.Header>{title}</Modal.Header>
      <ModalBody>
        <Description>{description}</Description>
        {bodyContent}
        {/* <WarningContainer content={warningContent} />  TODO: https://github.com/camunda/operate/issues/4020 */}
        <CmCheckbox ref={fieldRef} label={confirmationText} required />
      </ModalBody>
      <Modal.Footer>
        <Modal.SecondaryButton title="Cancel" onClick={onClose}>
          Cancel
        </Modal.SecondaryButton>
        <DeleteButton
          appearance="danger"
          label="Delete"
          onCmPress={async () => {
            await fieldRef.current?.renderValidity();
            if (fieldRef.current?.checked === true) {
              onDelete();
            }
          }}
          data-testid="delete-button"
        />
      </Modal.Footer>
    </Modal>
  );
};

export {DeleteDefinitionModal};
