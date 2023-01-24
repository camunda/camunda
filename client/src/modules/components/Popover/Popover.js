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
import {getRandomId, getScreenBounds} from 'services';

import './Popover.scss';

export default class Popover extends React.Component {
  constructor(props) {
    super(props);

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
    new MutationObserver(this.handleResize).observe(this.el, {childList: true, subtree: true});
    window.addEventListener('resize', this.handleResize);
  }

  componentWillUnmount() {
    document.body.removeChild(this.el);
    document.body.removeEventListener('click', this.close, {capture: true});
    window.removeEventListener('resize', this.handleResize);
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

  handleResize = () => {
    this.fixPositioning();
    this.calculateDialogStyle();
  };

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
        this.setState({open: false});
      }
      this.calculateDialogStyle();
      evt.insideClick = false;
    });
  };

  calculateDialogStyle = () => {
    const style = {};
    let scrollable = false;
    if (this.buttonRef && this.popoverDialogRef && this.popoverContentRef) {
      const overlayWidth = this.popoverDialogRef.clientWidth;
      const overlayHeight = this.popoverDialogRef.clientHeight;
      const contentHeight = this.popoverContentRef.clientHeight;
      const buttonRect = this.buttonRef.getBoundingClientRect();
      const screenBounds = getScreenBounds();

      const bodyWidth = document.body.clientWidth;
      const margin = 10;

      if (buttonRect.left + overlayWidth > bodyWidth) {
        style.right = 0;
      } else {
        style.left = 0;
      }

      if (
        overlayHeight + buttonRect.bottom > screenBounds.bottom - margin ||
        contentHeight > overlayHeight
      ) {
        style.height = screenBounds.bottom - buttonRect.bottom - 2 * margin + 'px';
        scrollable = true;
      }

      const topSpace = buttonRect.bottom - screenBounds.top;
      const bottomSpace = screenBounds.bottom - buttonRect.bottom;

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
    const {renderInPortal, children} = this.props;
    const {dialogStyles, scrollable} = this.state;
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
        <span className="dialogArrowBorder" style={arrowStyles} />
        <span className="dialogArrow" style={arrowStyles} />
        <div className="dialogContainer" style={dialogStyles}>
          <div
            ref={this.storePopoverDialogRef}
            onMouseDown={this.onPopoverDialogMouseDown}
            style={dialogStyles}
            className={classnames('dialog', {scrollable})}
          >
            <div ref={this.storePopoverContentRef}>{children}</div>
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

  storePopoverContentRef = (node) => {
    this.popoverContentRef = node;
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
    const {disabled, tooltip, icon, title, className, main, tooltipPosition} = this.props;
    const active = !disabled && this.state.open;
    return (
      <div
        onKeyDown={this.handleKeyPress}
        ref={this.storePopoverRootRef}
        className={classnames('Popover', className)}
      >
        <Tooltip content={tooltip} position={tooltipPosition}>
          <div className="buttonWrapper">
            <Button
              icon={icon && !title}
              active={active}
              main={main}
              onClick={this.toggleOpen}
              ref={this.storeButtonRef}
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
