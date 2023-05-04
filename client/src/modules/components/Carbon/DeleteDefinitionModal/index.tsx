/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Modal, Stack} from '@carbon/react';
import {Description} from './styled';

type Props = {
  isVisible: boolean;
  title: string;
  description: string;
  bodyContent: React.ReactNode;
  confirmationText: string;
  warningContent?: React.ReactNode; //TODO: make mandatory after https://github.com/camunda/operate/issues/4020
  onClose: () => void;
  onDelete: () => void;
};

const DeleteDefinitionModal: React.FC<Props> = ({
  isVisible,
  title,
  description,
  bodyContent,
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
      </Stack>
    </Modal>
  );
};

export {DeleteDefinitionModal};
