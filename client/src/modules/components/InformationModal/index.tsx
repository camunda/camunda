/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Modal, {SIZES} from 'modules/components/Modal';
import {Body} from './styled';

type Props = {
  isVisible: boolean;
  onClose: () => void;
  title: string;
  body: React.ReactNode;
  footer: React.ReactNode;
  size?: React.ComponentProps<typeof Modal>['size'];
} & Pick<React.ComponentProps<typeof Modal>, 'width' | 'maxHeight'>;

const InformationModal: React.FC<Props> = ({
  isVisible,
  onClose,
  title,
  body,
  footer,
  size,
  width,
  maxHeight,
}) => {
  return (
    <Modal
      onModalClose={onClose}
      isVisible={isVisible}
      size={size ?? SIZES.SMALL}
      width={width}
      maxHeight={maxHeight}
    >
      <Modal.Header>{title}</Modal.Header>
      <Body>
        <Modal.BodyText>{body}</Modal.BodyText>
      </Body>
      <Modal.Footer>{footer}</Modal.Footer>
    </Modal>
  );
};

export {InformationModal};
