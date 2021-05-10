/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ReactDOM from 'react-dom';
import classnames from 'classnames';

import {Button, Icon, Tooltip} from 'components';
import {getRandomId} from 'services';

import './Popover.scss';

export default class Popover extends React.Component {
  constructor(props) {
    super(props);
    this.initilizeFooterRef();

    this.el = document.createElement('div');
    this.id = getRandomId();
    this.insideClick = false;

    this.state = {
      open: !!props.autoOpen,
      dialogStyles: {},
    };
  }

  componentDidMount() {
    document.body.appendChild(this.el);
    this.mounted = true;
    document.body.addEventListener('click', this.close, {capture: true});
  }

  componentWillUnmount() {
    document.body.removeChild(this.el);
    document.body.removeEventListener('click', this.close, {capture: true});
    this.mounted = false;
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevState.open !== this.state.open) {
      if (this.state.open) {
        this.props.onOpen?.();
      } else {
        this.props.onClose?.();
      }
    }
  }

  toggleOpen = (evt) => {
    evt.preventDefault();
    const open = this.state.open;

    setTimeout(() => {
      this.setState({
        open: !open,
      });
      this.calculateDialogStyle();
    });
  };

  inSameScope = (evt) => {
    const modal = evt.target?.closest('.Modal');
    if (!modal) {
      return true;
    }
    return modal.contains(this.popoverDialogRef);
  };

  close = (evt) => {
    // We need to wait for the event delegation to be finished
    // so we know whether the click occured inside the popover,
    // in which case we do not want to close the popover
    setTimeout(() => {
      if (
        !(evt.popoverChain || []).includes(this.id) &&
        this.mounted &&
        !this.insideClick &&
        this.inSameScope(evt)
      ) {
        this.setState({
          open: false,
        });
        this.calculateDialogStyle();
      }
      this.insideClick = false;
    });
  };

  initilizeFooterRef() {
    if (!this.footerRef) {
      this.footerRef = document.body.querySelector('.Footer');
    }
  }

  calculateDialogStyle = () => {
    const style = {};
    let scrollable = false;
    if (this.buttonRef && this.popoverDialogRef) {
      const overlayWidth = this.popoverDialogRef.clientWidth;
      const overlayHeight = this.popoverDialogRef.clientHeight;
      const buttonLeftPosition = this.buttonRef.getBoundingClientRect().left;
      const buttonBottomPosition = this.buttonRef.getBoundingClientRect().bottom;
      this.initilizeFooterRef();
      const footerTop = this.footerRef?.getBoundingClientRect().top ?? 0;

      const bodyWidth = document.body.clientWidth;
      const margin = 10;

      if (buttonLeftPosition + overlayWidth > bodyWidth) {
        style.right = 0;
      } else {
        style.left = 0;
      }

      if (overlayHeight + buttonBottomPosition > footerTop - margin) {
        style.height = footerTop - buttonBottomPosition - 2 * margin + 'px';
        scrollable = true;
      }
    }

    this.setState({
      scrollable,
      dialogStyles: style,
    });
  };

  onPopoverDialogMouseDown = (evt) => {
    this.insideClick = true;
  };

  createOverlay = () => {
    const {renderInPortal} = this.props;

    let overlayStyles = {};
    let arrowStyles = {};
    if (renderInPortal) {
      const box = this.buttonRef.getBoundingClientRect();
      overlayStyles = {
        left: box.left,
        top: box.top + box.height,
      };

      arrowStyles = {
        left: box.width / 2,
      };
    }

    const markup = (
      <div
        className={classnames('overlay', this.props.renderInPortal, {
          Popover: this.props.renderInPortal,
        })}
        onClick={this.catchClick}
        style={overlayStyles}
      >
        <span className="Popover__dialog-arrow-border" style={arrowStyles}>
          {' '}
        </span>
        <span className="Popover__dialog-arrow" style={arrowStyles} />
        <div className="dialogContainer" style={this.state.dialogStyles}>
          <div
            ref={this.storePopoverDialogRef}
            onMouseDown={this.onPopoverDialogMouseDown}
            style={this.state.dialogStyles}
            className={classnames('Popover__dialog', {scrollable: this.state.scrollable})}
          >
            {this.props.children}{' '}
          </div>
        </div>
      </div>
    );

    if (renderInPortal) {
      return ReactDOM.createPortal(markup, this.el);
    }

    return markup;
  };

  storeButtonRef = (node) => {
    this.buttonRef = node;
  };

  storePopoverDialogRef = (node) => {
    this.popoverDialogRef = node;
  };

  storePopoverRootRef = (node) => {
    this.popoverRootRef = node;
  };

  catchClick = (evt) => {
    evt.nativeEvent.popoverChain = evt.nativeEvent.popoverChain || [];
    evt.nativeEvent.popoverChain.push(this.id);
  };

  handleKeyPress = (evt) => {
    if (evt.key === 'Escape' && this.popoverRootRef.contains(evt.target) && this.state.open) {
      evt.stopPropagation();
      this.setState({open: false});
    }
  };

  render() {
    const {disabled, tooltip, icon, title, className, main} = this.props;
    const active = !disabled && this.state.open;
    return (
      <div
        onKeyDown={this.handleKeyPress}
        ref={this.storePopoverRootRef}
        className={classnames('Popover', className)}
      >
        <Tooltip content={tooltip}>
          <div className="buttonWrapper">
            <Button
              icon={icon && !title}
              active={active}
              main={main}
              onClick={this.toggleOpen}
              ref={this.storeButtonRef}
              className="Popover__button"
              disabled={disabled}
            >
              {icon ? <Icon type={icon} /> : ''}
              {title}
              <Icon type="down" className="downIcon" />
            </Button>
          </div>
        </Tooltip>
        {active && this.createOverlay()}
      </div>
    );
  }
}
