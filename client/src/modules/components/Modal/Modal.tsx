/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps, ComponentPropsWithoutRef, forwardRef} from 'react';
import ReactDOM from 'react-dom';
import classnames from 'classnames';
import {ComposedModal, ModalBody, ModalHeader, ModalFooter} from '@carbon/react';

import './Modal.scss';

interface ModalProps extends ComponentProps<typeof ComposedModal> {
  isOverflowVisible?: boolean;
}

export default function Modal({children, className, isOverflowVisible, ...props}: ModalProps) {
  return typeof document === 'undefined'
    ? null
    : ReactDOM.createPortal(
        <ComposedModal
          {...props}
          className={classnames('Modal', {overflowVisible: isOverflowVisible}, className)}
        >
          {props.open && children}
        </ComposedModal>,
        document.body
      );
}

Modal.Header = ModalHeader;
Modal.Footer = ModalFooter;
Modal.Content = forwardRef<HTMLDivElement, ComponentPropsWithoutRef<'div'>>((props, ref) => {
  return (
    <ModalBody
      {...props}
      ref={ref}
      onClick={(evt) => {
        evt.stopPropagation();
        props.onClick?.(evt);
      }}
      onMouseDown={(evt) => {
        evt.stopPropagation();
        props.onMouseDown?.(evt);
      }}
      // carbon modals trigger blur when clicking on input element inside nested modals
      // This prevents focusing on those elements inside the nested modals
      onBlur={(evt) => {
        evt.stopPropagation();
        props.onBlur?.(evt);
      }}
    />
  );
});
