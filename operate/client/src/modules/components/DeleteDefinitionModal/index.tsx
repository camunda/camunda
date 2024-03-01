/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
