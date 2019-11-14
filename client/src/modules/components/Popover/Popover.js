/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Icon} from 'components';
import {getRandomId} from 'services';

import './Popover.scss';

export default class Popover extends React.Component {
  constructor(props) {
    super(props);
    this.initilizeFooterRef();

    this.id = getRandomId();
    this.insideClick = false;

    this.state = {
      open: false,
      dialogStyles: {}
    };
  }

  componentDidMount() {
    this.mounted = true;
    document.body.addEventListener('click', this.close);
  }

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close);
    this.mounted = false;
  }

  toggleOpen = evt => {
    evt.preventDefault();
    const open = this.state.open;

    setTimeout(() => {
      this.setState({
        open: !open
      });
      this.calculateDialogStyle();
    });
  };

  close = evt => {
    // We need to wait for the event delegation to be finished
    // so we know whether the click occured inside the popover,
    // in which case we do not want to close the popover
    setTimeout(() => {
      if (
        !(evt.popoverChain || []).includes(this.id) &&
        !evt.target.closest('.Modal') &&
        this.mounted &&
        !this.insideClick
      ) {
        this.setState({
          open: false
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
      const footerTop = this.footerRef.getBoundingClientRect().top;

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
      dialogStyles: style
    });
  };

  onPopoverDialogMouseDown = evt => {
    this.insideClick = true;
  };

  createOverlay = () => {
    return (
      <div onClick={this.catchClick}>
        <span className="Popover__dialog-arrow-border"> </span>
        <span className="Popover__dialog-arrow" />
        <div
          ref={this.storePopoverDialogRef}
          onMouseDown={this.onPopoverDialogMouseDown}
          style={this.state.dialogStyles}
          className={classnames('Popover__dialog', {scrollable: this.state.scrollable})}
        >
          {this.props.children}{' '}
        </div>
      </div>
    );
  };

  storeButtonRef = node => {
    this.buttonRef = node;
  };

  storePopoverDialogRef = node => {
    this.popoverDialogRef = node;
  };

  storePopoverRootRef = node => {
    this.popoverRootRef = node;
  };

  catchClick = evt => {
    evt.nativeEvent.popoverChain = evt.nativeEvent.popoverChain || [];
    evt.nativeEvent.popoverChain.push(this.id);
  };

  render() {
    const {disabled, tooltip, icon, title, className} = this.props;
    const active = !disabled && this.state.open;
    return (
      <div ref={this.storePopoverRootRef} className={classnames('Popover', className)}>
        <Button
          active={active}
          onClick={this.toggleOpen}
          ref={this.storeButtonRef}
          className="Popover__button"
          disabled={disabled}
          title={tooltip}
        >
          {icon ? <Icon type={icon} /> : ''}
          {title}
          <Icon type="down" className="downIcon" />
        </Button>
        {active && this.createOverlay()}
      </div>
    );
  }
}
