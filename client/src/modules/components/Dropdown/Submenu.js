/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';

import DropdownOption from './DropdownOption';
import {findLetterOption} from './service';

import {Icon} from 'components';

import './Submenu.scss';

export default class Submenu extends React.Component {
  constructor(props) {
    super(props);

    this.containerRef = React.createRef();
    this.menuObserver = new MutationObserver(this.calculatePlacement);

    this.state = {styles: {}, scrollable: false};
  }

  onClick = (evt) => {
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

  onMouseOver = (evt) => {
    if (this.props.disabled || this.props.open) {
      return;
    }
    if (this.props.onOpen) {
      this.props.onOpen(evt);
    }

    this.props.setOpened(evt);
  };

  onMouseLeave = (evt) => {
    if (this.props.disabled) {
      return;
    }
    this.props.setClosed(evt);
  };

  onKeyDown = (evt) => {
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

    if (String.fromCharCode(evt.keyCode).match(/(\w)/g)) {
      const options = Array.from(this.containerRef.current.querySelectorAll('.DropdownOption'));
      const matchedOption = findLetterOption(
        options,
        evt.key,
        options.indexOf(document.activeElement) + 1
      );

      if (matchedOption) {
        matchedOption.focus();
      }
    }
  };

  componentDidMount() {
    this.menuObserver.observe(this.containerRef.current, {
      childList: true,
      subtree: true,
    });

    this.initilizeHeaderAndFooterRefs();
  }

  componentWillUnmount() {
    this.menuObserver.disconnect();
  }

  componentDidUpdate(prevProps) {
    if (!prevProps.open && this.props.open) {
      document.activeElement.querySelector('[tabindex="0"]')?.focus();
    }

    if (prevProps.open && !this.props.open) {
      this.props.onClose?.();
    }
  }

  initilizeHeaderAndFooterRefs() {
    if (!this.footerRef || !document.body.contains(this.footerRef)) {
      this.footerRef = document.body.querySelector('.Footer');
    }
    if (!this.headerRef || !document.body.contains(this.headerRef)) {
      this.headerRef = document.body.querySelector('.Header');
    }
  }

  calculatePlacement = () => {
    const styles = {};
    const container = this.containerRef.current;
    const submenu = container.querySelector('.childrenContainer');
    if (submenu) {
      const parentMenu = container.getBoundingClientRect();
      const body = document.body;

      if (parentMenu.right + submenu.clientWidth > body.clientWidth) {
        styles.right = this.props.offset + 'px';
      } else {
        styles.left = this.props.offset + 'px';
      }

      const margin = 10;
      this.initilizeHeaderAndFooterRefs();
      const footerTop = this.footerRef.getBoundingClientRect().top;
      const headerBottom = this.headerRef.getBoundingClientRect().bottom;

      const bottomAvailableHeight = footerTop - parentMenu.top - margin;
      if (submenu.clientHeight > bottomAvailableHeight) {
        let shiftDistance = submenu.clientHeight - bottomAvailableHeight;

        const topAvailableHeight = parentMenu.top - headerBottom - margin;
        if (shiftDistance > topAvailableHeight) {
          shiftDistance = topAvailableHeight;
        }

        styles.top = '-' + shiftDistance + 'px';
        styles.maxHeight = footerTop - headerBottom - 2 * margin;
      }
    }

    this.setState({styles});
  };

  render() {
    return (
      <DropdownOption
        checked={this.props.checked}
        disabled={this.props.disabled}
        className={classnames('Submenu', {
          open: this.props.open,
          fixed: this.props.fixed,
        })}
        ref={this.containerRef}
        onClick={this.onClick}
        onMouseOver={this.onMouseOver}
        onMouseLeave={this.onMouseLeave}
        onKeyDown={(evt) => {
          if (evt.key === 'ArrowRight' && !this.props.disabled) {
            this.props.forceToggle(evt);
          }
        }}
      >
        {this.props.label}
        <Icon type="right" className="rightIcon" />
        {this.props.open && (
          <div
            className="childrenContainer"
            style={this.state.styles}
            onKeyDown={this.onKeyDown}
            onClick={this.props.closeParent}
            onMouseEnter={this.props.onMenuMouseEnter}
            onMouseLeave={this.props.onMenuMouseLeave}
          >
            <div className="hoverGuard" />
            {this.props.children}
          </div>
        )}
      </DropdownOption>
    );
  }
}
