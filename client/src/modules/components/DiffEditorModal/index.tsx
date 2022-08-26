/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Modal, {SIZES} from 'modules/components/Modal';
import {Body} from './styled';
import {DiffEditor} from 'modules/components/DiffEditor';
import {beautifyJSON} from 'modules/utils/editor/beautifyJSON';

type Props = {
  originalValue: string;
  modifiedValue: string;
  isVisible: boolean;
  onClose: () => void;
  title: string;
};

const DiffEditorModal: React.FC<Props> = ({
  originalValue,
  modifiedValue,
  isVisible,
  onClose,
  title,
}) => {
  return (
    <Modal
      onModalClose={() => {
        onClose();
      }}
      size={SIZES.BIG}
      isVisible={isVisible}
      preventKeyboardEvents
    >
      <Modal.Header aria-label={title}>{title}</Modal.Header>
      <Body>
        <DiffEditor
          originalValue={beautifyJSON(originalValue)}
          modifiedValue={beautifyJSON(modifiedValue)}
        />
      </Body>
      <Modal.Footer>
        <Modal.PrimaryButton
          onClick={() => {
            onClose();
          }}
        >
          Close
        </Modal.PrimaryButton>
      </Modal.Footer>
    </Modal>
  );
};

export {DiffEditorModal};
