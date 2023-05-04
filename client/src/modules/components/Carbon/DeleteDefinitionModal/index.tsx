/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Modal, Stack, ActionableNotification} from '@carbon/react';
import {Description, WarningContainer} from './styled';

type Props = {
  isVisible: boolean;
  title: string;
  description: string;
  bodyContent: React.ReactNode;
  confirmationText: string;
  warningTitle?: string; //TODO: make mandatory after https://github.com/camunda/operate/issues/4020
  warningContent?: React.ReactNode; //TODO: make mandatory after https://github.com/camunda/operate/issues/4020
  onClose: () => void;
  onDelete: () => void;
};

const DeleteDefinitionModal: React.FC<Props> = ({
  isVisible,
  title,
  description,
  bodyContent,
  warningTitle,
  warningContent,
  onClose,
  onDelete,
}) => {
  return (
    <Modal
      open={isVisible}
      danger
      preventCloseOnClickOutside
      modalHeading={title}
      primaryButtonText="Delete"
      secondaryButtonText="Cancel"
      onRequestSubmit={onDelete}
      onRequestClose={onClose}
      size="md"
    >
      <Stack gap={6}>
        <Description>{description}</Description>
        {bodyContent}
        {warningContent && (
          <ActionableNotification
            kind="warning"
            inline
            hideCloseButton
            lowContrast
            title={warningTitle}
            children={<WarningContainer>{warningContent}</WarningContainer>}
            actionButtonLabel=""
          />
        )}
      </Stack>
    </Modal>
  );
};

export {DeleteDefinitionModal};
