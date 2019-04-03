/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ReactDOM from 'react-dom';
import classnames from 'classnames';

import './Modal.scss';

export default class Modal extends React.Component {
  constructor(props) {
    super(props);
    this.el = document.createElement('div');
  }

  componentDidMount() {
    document.body.appendChild(this.el);
    this.fixPositioning();
    this.setFocus();
    new MutationObserver(this.fixPositioning).observe(this.el, {childList: true, subtree: true});
    window.addEventListener('resize', this.fixPositioning);
    window.addEventListener('keydown', this.handleKeyPress);
  }

  componentWillUnmount() {
    document.body.removeChild(this.el);
    window.removeEventListener('resize', this.fixPositioning);
    window.removeEventListener('keydown', this.handleKeyPress);
  }

  storeContainer = node => {
    this.container = node;
  };

  fixPositioning = () => {
    if (this.container) {
      const windowHeight = Math.max(document.documentElement.clientHeight, window.innerHeight || 0);
      const margin = 30; // already set top  (15 px) + bottom  (15 px) margin
      let topMargin = (windowHeight - this.container.clientHeight) / 2 - margin;
      topMargin = Math.max(topMargin, 0);
      this.container.style.marginTop = topMargin + 'px';
      this.container.style.marginLeft = -this.container.clientWidth / 2 + 'px';
    }
  };

  setFocus = () => {
    if (this.container && this.props.open) {
      this.container.focus();
    }
  };

  onBackdropClick = evt => {
    evt.stopPropagation();

    const handler = this.props.onClose;
    handler && handler();
  };

  catchClick = evt => {
    if (!evt.nativeEvent.isCloseEvent) {
      evt.stopPropagation();
    }
  };

  handleKeyPress = evt => {
    if (evt.key === 'Escape') {
      const handler = this.props.onClose;
      handler && handler();
    } else if (evt.key === 'Enter' && this.props.open) {
      const handler = this.props.onConfirm;
      handler && handler(evt);
    }
  };

  render() {
    const {open, children} = this.props;

    if (open) {
      return ReactDOM.createPortal(
        <div className="Modal" onClick={this.onBackdropClick}>
          <div className="Modal__scroll-container">
            <div
              className={classnames('Modal__content-container', this.props.className, {
                ['Modal__content-container--' + this.props.size]: this.props.size
              })}
              tabIndex="-1"
              ref={this.storeContainer}
              onClick={this.catchClick}
            >
              {children}
            </div>
          </div>
        </div>,
        this.el
      );
    }

    return null;
  }

  componentDidUpdate(prevProps) {
    if (!prevProps.open && this.props.open) {
      this.setFocus();
    }

    this.fixPositioning();
  }
}

Modal.Header = function({children}) {
  return (
    <div className="Modal__header">
      <h1 className="Modal__heading">{children}</h1>
      <button
        className="Modal__close-button"
        onClick={evt => (evt.nativeEvent.isCloseEvent = true)}
      />
    </div>
  );
};

Modal.Content = function({className, children}) {
  return <div className={classnames('Modal__content', className)}>{children}</div>;
};

Modal.Actions = function({children}) {
  return <div className="Modal__actions">{children}</div>;
};
