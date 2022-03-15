/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {createPortal} from 'react-dom';

import {Button} from 'modules/components/Button';
import * as Styled from './styled';

const ModalContext = React.createContext({});

function withModal(Component: any) {
  function WithModal(props: any) {
    return (
      <ModalContext.Consumer>
        {(contextValue) => <Component {...props} {...contextValue} />}
      </ModalContext.Consumer>
    );
  }

  WithModal.WrappedComponent = Component;

  WithModal.displayName = `WithModal(${
    Component.displayName || Component.name || 'Component'
  })`;

  return WithModal;
}

type ModalProps = {
  onModalClose: () => void;
  isVisible: boolean;
  className?: string;
  size: 'SMALL' | 'BIG';
  preventKeyboardEvents?: boolean;
};

class Modal extends React.Component<ModalProps> {
  static Header: any;
  static Body = Styled.ModalBody;
  static BodyText = Styled.ModalBodyText;
  static Footer: any;
  static PrimaryButton: any;
  static SecondaryButton: any;

  eventListenerAdded: any;
  keyHandlers: any;
  modalRef: any;
  prevActiveElement: any;

  constructor(props: ModalProps) {
    super(props);
    this.keyHandlers = new Map([
      [27, this.props.onModalClose],
      [9, this.handleTabKeyDown],
    ]);
    this.modalRef = React.createRef();
    this.eventListenerAdded = false;
  }

  componentDidMount() {
    if (this.props.isVisible) {
      this.addEventListener();
    }
  }

  componentDidUpdate() {
    const {isVisible} = this.props;
    const {eventListenerAdded} = this;

    if (isVisible && !eventListenerAdded) {
      this.addEventListener();
    } else if (!isVisible && eventListenerAdded) {
      this.removeEventListener();
    }
  }

  componentWillUnmount() {
    document.removeEventListener('keydown', this.handleKeyDown);
  }

  addEventListener() {
    if (!this.props.preventKeyboardEvents) {
      this.eventListenerAdded = true;
      this.prevActiveElement = document.activeElement as HTMLElement;

      this.prevActiveElement && this.prevActiveElement.blur();
      document.addEventListener('keydown', this.handleKeyDown);
    }
  }

  removeEventListener() {
    this.eventListenerAdded = false;

    document.removeEventListener('keydown', this.handleKeyDown);
    this.prevActiveElement.focus();
  }

  handleKeyDown = (e: any) => {
    const keyHandler = this.keyHandlers.get(e.keyCode);
    return keyHandler && keyHandler(e);
  };

  handleTabKeyDown = (e: any) => {
    const focusableModalElements = [
      ...this.modalRef?.current.querySelectorAll(
        'a[href], button, textarea, code, input[type="text"], input[type="radio"], input[type="checkbox"], select'
      ),
    ].filter((element) => !!element.disabled === false);

    const firstElement = focusableModalElements[0];
    const lastElement =
      focusableModalElements[focusableModalElements.length - 1];
    const indexOfActiveElement = [...focusableModalElements].indexOf(
      document.activeElement
    );

    const isLastElement =
      indexOfActiveElement === focusableModalElements.length - 1;
    const isOutsideModal = indexOfActiveElement < 0;
    const isFirstElement = indexOfActiveElement === 0;

    if (!e.shiftKey && (isLastElement || isOutsideModal)) {
      firstElement.focus();
      e.preventDefault();
    }

    if (e.shiftKey && (isFirstElement || isOutsideModal)) {
      lastElement.focus();
      e.preventDefault();
    }
  };

  addKeyHandler = (keyCode: any, handler: any) =>
    this.keyHandlers.set(keyCode, handler);

  render() {
    const {onModalClose, children, className, isVisible, size} = this.props;
    return createPortal(
      <Styled.Transition
        in={isVisible}
        timeout={200}
        mountOnEnter
        unmountOnExit
      >
        <Styled.ModalRoot
          className={className}
          data-testid="modal"
          ref={this.modalRef}
          role="dialog"
        >
          <Styled.ModalContent size={size}>
            <ModalContext.Provider
              value={{
                onModalClose,
                addKeyHandler: this.addKeyHandler,
              }}
            >
              {children}
            </ModalContext.Provider>
          </Styled.ModalContent>
        </Styled.ModalRoot>
      </Styled.Transition>,
      document.body
    );
  }
}

type HeaderProps = {
  children: React.ReactNode;
};

const Header: React.FC<HeaderProps> = function ({children, ...props}) {
  return (
    <Styled.ModalHeader {...props}>
      {children}
      <ModalContext.Consumer>
        {(modalContext) => (
          <Styled.CrossButton
            data-testid="cross-button"
            // @ts-expect-error ts-migrate(2339) FIXME: Property 'onModalClose' does not exist on type '{}... Remove this comment to see the full error message
            onClick={modalContext.onModalClose}
            title="Exit Modal"
          >
            <Styled.CrossIcon />
          </Styled.CrossButton>
        )}
      </ModalContext.Consumer>
    </Styled.ModalHeader>
  );
};

Modal.Header = Header;

type FooterProps = {
  className?: string;
  children?: React.ReactNode;
};

const Footer: React.FC<FooterProps> = function (props) {
  return (
    // @ts-expect-error ts-migrate(2769) FIXME: Property 'className' does not exist on type 'Intri... Remove this comment to see the full error message
    <Styled.ModalFooter className={props.className}>
      {props.children}
    </Styled.ModalFooter>
  );
};

Modal.Footer = Footer;

type ModalPrimaryButtonProps = {
  addKeyHandler: (...args: any[]) => any;
  disableKeyBinding?: boolean;
  className?: string;
};

class ModalPrimaryButton extends React.Component<ModalPrimaryButtonProps> {
  componentDidMount() {
    !this.props.disableKeyBinding &&
      this.props.addKeyHandler(13, this.handleReturnKeyPress);
  }

  primaryButtonRef = React.createRef<HTMLButtonElement>();

  handleReturnKeyPress = (e: any) => {
    e.preventDefault();
    // @ts-expect-error ts-migrate(2571) FIXME: Object is of type 'unknown'.
    this.primaryButtonRef.current.click();
  };

  render() {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'onModalClose' does not exist on type 'Re... Remove this comment to see the full error message
    const {onModalClose, ...props} = this.props;
    return (
      <Button
        ref={this.primaryButtonRef}
        color="primary"
        size="medium"
        {...props}
      />
    );
  }
}

type ModalSecondaryButtonProps = {
  addKeyHandler: (...args: any[]) => any;
  className?: string;
};

class ModalSecondaryButton extends React.Component<ModalSecondaryButtonProps> {
  render() {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'onModalClose' does not exist on type 'Re... Remove this comment to see the full error message
    const {onModalClose, ...props} = this.props;
    return <Button color="secondary" size="medium" {...props} />;
  }
}

Modal.PrimaryButton = withModal(ModalPrimaryButton);
Modal.SecondaryButton = withModal(ModalSecondaryButton);

export {SIZES} from './constants';
export default Modal;
