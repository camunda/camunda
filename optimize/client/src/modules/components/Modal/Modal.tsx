/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Children,
  ComponentProps,
  ComponentPropsWithoutRef,
  ReactElement,
  ReactNode,
  createElement,
  forwardRef,
  isValidElement,
} from 'react';
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
          aria-label={getAriaLabel(children)}
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

// In @carbon/react implementation Modal's aria label comes from ModalHeader's label prop.
// Because we are using our own Modal.Header component, and we only use title and not label, we have to prepare the aria label manually.
// See ComposedModal.tsx from @carbon/react for reference:
// https://github.com/carbon-design-system/carbon/blob/main/packages/react/src/components/ComposedModal/ComposedModal.tsx
function getAriaLabel(children: ReactNode): string | undefined {
  const headerElement = Children.toArray(children).find(
    (child): child is ReactElement<ComponentProps<typeof Modal.Header>, typeof Modal.Header> =>
      isValidElement(child) && child.type === createElement(Modal.Header).type
  );

  if (!headerElement) {
    return undefined;
  }

  const {title, label} = headerElement.props;

  return getReactNodeTextContent(label || title);
}

function getReactNodeTextContent(node: ReactNode): string {
  if (!node) {
    return '';
  }

  if (typeof node === 'string' || typeof node === 'number') {
    return node.toString();
  }
  if (node instanceof Array) {
    return node.map(getReactNodeTextContent).join('');
  }
  if (isValidElement(node)) {
    return getReactNodeTextContent(node.props.children);
  }

  return '';
}
