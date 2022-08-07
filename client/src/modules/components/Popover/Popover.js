/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

    this.state = {
      open: !!props.autoOpen,
      dialogStyles: {},
    };
  }

  componentDidMount() {
    document.body.appendChild(this.el);
    this.mounted = true;
    document.body.addEventListener('click', this.close, {capture: true});
    new MutationObserver(this.fixPositioning).observe(this.el, {childList: true, subtree: true});
    window.addEventListener('resize', this.fixPositioning);
  }

  componentWillUnmount() {
    document.body.removeChild(this.el);
    document.body.removeEventListener('click', this.close, {capture: true});
    window.removeEventListener('resize', this.fixPositioning);
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

    this.fixPositioning();
  }

  fixPositioning = () => {
    const {renderInPortal} = this.props;
    const overlay = this.el.querySelector('.overlay');
    if (renderInPortal && overlay) {
      const box = this.buttonRef.getBoundingClientRect();

      overlay.style.left = box.left + 'px';
      overlay.style.top = box.top + box.height + 'px';
      overlay.style.width = box.width + 'px';
    }
  };

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

  close = (evt) => {
    // We need to wait for the event delegation to be finished
    // so we know whether the click occured inside the popover,
    // in which case we do not want to close the popover
    setTimeout(() => {
      if (!(evt.popoverChain || []).includes(this.id) && this.mounted && !evt.insideClick) {
        this.setState({
          open: false,
        });
        this.calculateDialogStyle();
      }
      evt.insideClick = false;
    });
  };

  initilizeFooterRef() {
    if (!this.footerRef) {
      this.footerRef = document.body.querySelector('.Footer');
    }
    if (!this.headerRef) {
      this.headerRef = document.body.querySelector('.Header');
    }
  }

  calculateDialogStyle = () => {
    const style = {};
    let scrollable = false;
    if (this.buttonRef && this.popoverDialogRef) {
      const overlayWidth = this.popoverDialogRef.clientWidth;
      const overlayHeight = this.popoverDialogRef.clientHeight;
      const buttonRect = this.buttonRef.getBoundingClientRect();
      this.initilizeFooterRef();
      const footerTop = this.footerRef?.getBoundingClientRect().top ?? window.innerHeight;
      const headerBottom = this.headerRef?.getBoundingClientRect().bottom ?? 0;

      const bodyWidth = document.body.clientWidth;
      const margin = 10;

      if (buttonRect.left + overlayWidth > bodyWidth) {
        style.right = 0;
      } else {
        style.left = 0;
      }

      if (overlayHeight + buttonRect.bottom > footerTop - margin) {
        style.height = footerTop - buttonRect.bottom - 2 * margin + 'px';
        scrollable = true;
      }

      const topSpace = buttonRect.bottom - headerBottom;
      const bottomSpace = footerTop - buttonRect.bottom;

      if (this.props.renderInPortal && bottomSpace < overlayHeight && topSpace > bottomSpace) {
        // flip vertically
        style.bottom = buttonRect.height + margin;
        scrollable = overlayHeight > topSpace;
        style.height = scrollable ? topSpace : overlayHeight;
      }
    }

    this.setState({
      scrollable,
      dialogStyles: style,
    });
  };

  createOverlay = () => {
    const {renderInPortal} = this.props;
    const {dialogStyles} = this.state;
    let arrowStyles = {};

    if (dialogStyles.bottom) {
      // flip arrow vertically
      arrowStyles.top = -dialogStyles.bottom;
      arrowStyles.transform = 'rotate(180deg)';
    }

    const markup = (
      <div
        className={classnames('overlay', renderInPortal, {
          Popover: renderInPortal,
        })}
        // we use the capture phase because some components (e.g. modals)
        // stop propagating the events to the document
        onClickCapture={this.catchClick}
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
            {this.props.children}
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
    evt.nativeEvent.insideClick = true;
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
