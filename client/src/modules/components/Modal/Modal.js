/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {createPortal} from 'react-dom';

import Button from 'modules/components/Button';

import * as Styled from './styled';

const ModalContext = React.createContext({});

function withModal(Component) {
  function WithModal(props) {
    return (
      <ModalContext.Consumer>
        {contextValue => <Component {...props} {...contextValue} />}
      </ModalContext.Consumer>
    );
  }

  WithModal.WrappedComponent = Component;

  WithModal.displayName = `WithModal(${Component.displayName ||
    Component.name ||
    'Component'})`;

  return WithModal;
}

export default class Modal extends React.Component {
  static propTypes = {
    onModalClose: PropTypes.func.isRequired,
    isVisible: PropTypes.bool.isRequired,
    children: PropTypes.node,
    className: PropTypes.string
  };

  constructor(props) {
    super(props);
    this.keyHandlers = new Map([
      [27, this.props.onModalClose],
      [9, this.handleTabKeyDown]
    ]);
    this.modalRef = React.createRef();
    this.eventListenerAdded = false;
  }

  componentDidMount() {
    if (this.props.isVisible) {
      this.addEventListner();
    }
  }

  componentDidUpdate() {
    const {isVisible} = this.props;
    const {eventListenerAdded} = this;

    if (isVisible && !eventListenerAdded) {
      this.addEventListner();
    } else if (!isVisible && eventListenerAdded) {
      this.removeEventListner();
    }
  }

  addEventListner() {
    this.eventListenerAdded = true;
    this.prevActiveElement = document.activeElement;

    document.activeElement && document.activeElement.blur();
    document.addEventListener('keydown', this.handleKeyDown);
  }

  removeEventListner() {
    this.eventListenerAdded = false;

    document.removeEventListener('keydown', this.handleKeyDown);
    this.prevActiveElement.focus();
  }

  handleKeyDown = e => {
    const keyHandler = this.keyHandlers.get(e.keyCode);
    return keyHandler && keyHandler(e);
  };

  handleTabKeyDown = e => {
    const focusableModalElements = [
      ...this.modalRef.current.querySelectorAll(
        'a[href], button, textarea, code, input[type="text"], input[type="radio"], input[type="checkbox"], select'
      )
    ].filter(element => !!element.disabled === false);

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

  addKeyHandler = (keyCode, handler) => this.keyHandlers.set(keyCode, handler);

  render() {
    const {onModalClose, children, className, isVisible} = this.props;
    return createPortal(
      <Styled.Transition
        in={isVisible}
        timeout={200}
        mountOnEnter
        unmountOnExit
      >
        <Styled.ModalRoot
          className={className}
          ref={this.modalRef}
          role="dialog"
        >
          <Styled.ModalContent>
            <ModalContext.Provider
              value={{
                onModalClose,
                addKeyHandler: this.addKeyHandler
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

Modal.Header = function ModalHeader({children, ...props}) {
  return (
    <Styled.ModalHeader {...props}>
      {children}
      <ModalContext.Consumer>
        {modalContext => (
          <Styled.CrossButton
            data-test="cross-button"
            onClick={modalContext.onModalClose}
            title="Close Modal"
          >
            <Styled.CrossIcon />
          </Styled.CrossButton>
        )}
      </ModalContext.Consumer>
    </Styled.ModalHeader>
  );
};

Modal.Header.propTypes = {
  children: PropTypes.node
};

Modal.Body = Styled.ModalBody;
Modal.BodyText = Styled.ModalBodyText;

Modal.Footer = function ModalFooter(props) {
  return (
    <Styled.ModalFooter className={props.className}>
      {props.children}
    </Styled.ModalFooter>
  );
};

Modal.Footer.propTypes = {
  className: PropTypes.string,
  children: PropTypes.node
};

class ModalPrimaryButton extends React.Component {
  static propTypes = {
    addKeyHandler: PropTypes.func.isRequired,
    disableKeyBinding: PropTypes.bool,
    className: PropTypes.string,
    children: PropTypes.node
  };

  componentDidMount() {
    !this.props.disableKeyBinding &&
      this.props.addKeyHandler(13, this.handleReturnKeyPress);
  }

  primaryButtonRef = React.createRef();

  handleReturnKeyPress = e => {
    e.preventDefault();
    this.primaryButtonRef.current.click();
  };

  render() {
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

class ModalSecondatyButton extends React.Component {
  static propTypes = {
    addKeyHandler: PropTypes.func.isRequired,
    className: PropTypes.string,
    children: PropTypes.node
  };

  render() {
    const {onModalClose, ...props} = this.props;
    return <Button color="secondary" size="medium" {...props} />;
  }
}

Modal.PrimaryButton = withModal(ModalPrimaryButton);
Modal.SecondaryButton = withModal(ModalSecondatyButton);
