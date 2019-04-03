/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import DropdownOption from './DropdownOption';

import {Icon} from 'components';

import './Submenu.scss';

export default class Submenu extends React.Component {
  state = {putLeft: false};
  containerRef = React.createRef();

  onClick = evt => {
    if (this.props.disabled) {
      return;
    }

    if (this.props.onClick) {
      this.props.onClick(evt);
    }
    if (this.props.onOpen) {
      this.props.onOpen(evt);
    }
    this.props.forceToggle(evt);
  };

  onMouseOver = evt => {
    if (this.props.disabled || this.props.open) {
      return;
    }
    if (this.props.onOpen) {
      this.props.onOpen(evt);
    }

    this.props.setOpened(evt);
  };

  onMouseLeave = evt => {
    this.props.setClosed(evt);
  };

  onKeyDown = evt => {
    evt.stopPropagation();
    if (evt.key !== 'Tab') {
      evt.preventDefault();
    }

    if (evt.key === 'Enter') {
      evt.target.click();
    }

    if (evt.key === 'Escape' || evt.key === 'ArrowLeft') {
      document.activeElement.parentNode.closest('.DropdownOption').focus();
      this.props.forceToggle(evt);
    }

    if (evt.key === 'ArrowDown') {
      const next = document.activeElement.nextElementSibling;
      if (next) {
        next.focus();
      }
    }

    if (evt.key === 'ArrowUp') {
      const previous = document.activeElement.previousElementSibling;
      if (previous) {
        previous.focus();
      }
    }
  };

  componentDidMount() {
    new MutationObserver(this.calculatePlacement).observe(this.containerRef.current, {
      childList: true,
      subtree: true
    });
  }

  calculatePlacement = () => {
    const container = this.containerRef.current;
    const submenu = container.querySelector('.childrenContainer');
    if (submenu) {
      const submenuWidth = submenu.clientWidth;
      const parentRightEdge = container.getBoundingClientRect().right;
      const bodyWidth = document.body.clientWidth;

      if (parentRightEdge + submenuWidth > bodyWidth) {
        this.setState({putLeft: true});
      } else {
        this.setState({putLeft: false});
      }
    }
  };

  render() {
    const placementProp = this.state.putLeft ? 'right' : 'left';
    return (
      <DropdownOption
        checked={this.props.checked}
        disabled={this.props.disabled}
        className={classnames('Submenu', {
          open: this.props.open
        })}
        ref={this.containerRef}
        onClick={this.onClick}
        onMouseOver={this.onMouseOver}
        onMouseLeave={this.onMouseLeave}
      >
        {this.props.label}
        <Icon type="right" className="rightIcon" />
        {this.props.open && (
          <div
            onClick={this.props.closeParent}
            onKeyDown={this.onKeyDown}
            className="childrenContainer"
            style={{[placementProp]: this.props.offset - 1 + 'px'}}
          >
            {this.props.children}
          </div>
        )}
      </DropdownOption>
    );
  }
}
