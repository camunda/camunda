/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Icon} from 'components';

import './Popover.scss';

export default class Popover extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      open: false,
      dialogStyles: {}
    };
  }

  componentDidMount() {
    this.mounted = true;
    document.body.addEventListener('click', this.close);
    new MutationObserver(this.calculateDialogStyle).observe(this.popoverRootRef, {
      childList: true,
      subtree: true
    });
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
    });
  };

  close = evt => {
    // We need to wait for the event delegation to be finished
    // so we know whether the click occured inside the popover,
    // in which case we do not want to close the popover
    setTimeout(() => {
      if (!evt.inOverlay && this.mounted) {
        this.setState({
          open: false
        });
      }
    });
  };

  calculateDialogStyle = () => {
    const style = {};
    if (this.buttonRef && this.popoverDialogRef) {
      const overlayWidth = this.popoverDialogRef.clientWidth;
      const buttonPosition = this.buttonRef.getBoundingClientRect().left;

      const bodyWidth = document.body.clientWidth;

      if (buttonPosition + overlayWidth > bodyWidth) {
        style.right = 0;
      } else {
        style.left = 0;
      }
    }

    this.setState({
      dialogStyles: style
    });
  };

  createOverlay = () => {
    return (
      <div onClick={this.catchClick}>
        <span className="Popover__dialog-arrow-border"> </span>
        <span className="Popover__dialog-arrow" />
        <div
          ref={this.storePopoverDialogRef}
          style={this.state.dialogStyles}
          className="Popover__dialog"
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
    evt.nativeEvent.inOverlay = true;
  };

  render() {
    return (
      <div ref={this.storePopoverRootRef} className={classnames('Popover', this.props.className)}>
        <Button
          active={this.state.open}
          onClick={this.toggleOpen}
          ref={this.storeButtonRef}
          className="Popover__button"
          disabled={this.props.disabled}
          title={this.props.tooltip}
        >
          {this.props.icon ? <Icon type={this.props.icon} /> : ''}
          {this.props.title}
        </Button>
        {this.state.open && this.createOverlay()}
      </div>
    );
  }
}
