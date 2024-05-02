/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal, Stack, ActionableNotification, Checkbox} from '@carbon/react';
import {Description, WarningContainer} from './styled';
import {useState} from 'react';

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
  confirmationText,
  warningContent,
  onClose,
  onDelete,
}) => {
  const [isConfirmed, setIsConfirmed] = useState(false);
  const [hasConfirmationError, setHasConfirmationError] = useState(false);

  return (
    <Modal
      open={isVisible}
      danger
      preventCloseOnClickOutside
      modalHeading={title}
      primaryButtonText="Delete"
      secondaryButtonText="Cancel"
      onRequestSubmit={() => {
        if (!isConfirmed) {
          setHasConfirmationError(true);
          return;
        }

        onDelete();
        setIsConfirmed(false);
      }}
      onRequestClose={() => {
        setHasConfirmationError(false);
        onClose();
        setIsConfirmed(false);
      }}
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
        <Checkbox
          checked={isConfirmed}
          id="confirmation-checkbox"
          labelText={confirmationText}
          invalid={hasConfirmationError}
          invalidText="Please tick this box if you want to proceed."
          warnText=""
          onChange={(_, {checked}) => {
            if (checked && hasConfirmationError) {
              setHasConfirmationError(false);
            }

            setIsConfirmed(checked);
          }}
        />
      </Stack>
    </Modal>
  );
};

export {DeleteDefinitionModal};
