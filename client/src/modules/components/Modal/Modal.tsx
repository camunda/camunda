/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import ReactDOM from 'react-dom';
import classnames from 'classnames';
import {ComposedModal, ModalBody, ModalHeader, ModalFooter} from '@carbon/react';

import './Modal.scss';

type ModalProps = {
  onClose?: () => void;
  size?: string;
  open?: boolean;
  className?: string;
  children?: ReactNode;
  isFullWidth?: boolean;
  isOverflowVisible?: boolean;
};

export default function Modal({
  open,
  onClose,
  children,
  className,
  size,
  isFullWidth,
  isOverflowVisible,
}: ModalProps) {
  return typeof document === 'undefined'
    ? null
    : ReactDOM.createPortal(
        <ComposedModal
          className={classnames('CarbonModal', {overflowVisible: isOverflowVisible}, className)}
          open={open}
          onClose={onClose}
          size={size}
          isFullWidth={isFullWidth}
        >
          {children}
        </ComposedModal>,
        document.body
      );
}

Modal.Header = ModalHeader;
Modal.Footer = ModalFooter;
Modal.Content = ModalBody;
