/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Icon} from 'components';
import {getScreenBounds} from 'services';

import DropdownOption from './DropdownOption';
import Submenu from './Submenu';
import DropdownOptionsList from './DropdownOptionsList';
import {findLetterOption} from './service';

import './Dropdown.scss';

export default class Dropdown extends React.Component {
  menuContainer = React.createRef();

  constructor(props) {
    super(props);

    this.state = {
      open: false,
      menuStyle: {right: 0},
      listStyles: {},
    };
  }

  toggleOpen = (evt) => {
    evt.preventDefault();

    const {disabled, onOpen} = this.props;

    if (!disabled) {
      const newOpenState = !this.state.open;
      this.setState({open: newOpenState}, () => onOpen?.(newOpenState));
      this.calculateMenuStyle(newOpenState);
    }
  };

  close = ({target}) => {
    if (this.state.open && !this.container.contains(target)) {
      this.setState({open: false});
      this.calculateMenuStyle(false);
    }
  };

  handleScroll = ({target}) => {
    if (target.contains(this.container)) {
      this.close({});
    }
  };

  componentDidMount() {
    document.body.addEventListener('click', this.close, true);
    document.body.addEventListener('scroll', this.handleScroll, true);
    new MutationObserver(this.fixPositioning).observe(this.container, {
      childList: true,
      subtree: true,
    });
    window.addEventListener('resize', this.fixPositioning);
  }

  fixPositioning = () => {
    const {open} = this.state;
    open && this.calculateMenuStyle(open);
  };

  calculateMenuStyle = (open) => {
    const activeButton = this.container.querySelector('.activateButton');
    const menuStyle = {minWidth: this.container.clientWidth + 'px'};
    const listStyles = {};
    let scrollable = false;
    const margin = 10;

    const bodyWidth = document.body.clientWidth;
    const overlay = this.menuContainer.current;
    const buttonPosition = activeButton.getBoundingClientRect();
    const screenBounds = getScreenBounds();
    const offsetParent = activeButton.offsetParent.getBoundingClientRect();

    if (open) {
      menuStyle.top = buttonPosition.top - offsetParent.top + activeButton.offsetHeight;
    }

    menuStyle.left = buttonPosition.left - offsetParent.left;

    // check to flip menu horizentally
    if (buttonPosition.left + overlay.clientWidth > bodyWidth) {
      menuStyle.left -= overlay.clientWidth - buttonPosition.width;
    }

    if (open && buttonPosition.bottom + overlay.clientHeight > screenBounds.bottom) {
      scrollable = true;
      listStyles.height = screenBounds.bottom - buttonPosition.bottom - margin;

      // check to flip menu vertically
      if (buttonPosition.bottom + overlay.clientHeight > screenBounds.bottom) {
        menuStyle.top -= overlay.clientHeight + buttonPosition.height + 6; // 2 x 3px menu margin
        if (buttonPosition.top - screenBounds.top >= overlay.clientHeight) {
          scrollable = false;
          listStyles.height = 'auto';
        }
      }
    }

    this.setState({menuStyle, listStyles, scrollable});
  };

  handleKeyPress = (evt) => {
    evt.stopPropagation();

    const options = Array.from(
      this.container.querySelectorAll('.activateButton, li > :not([disabled])')
    );

    evt = evt || window.event;
    const selectedOption = options.indexOf(document.activeElement);

    if (evt.key !== 'Tab') {
      evt.preventDefault();
    }

    if (evt.key === 'Enter') {
      evt.target.click();
    }

    if (evt.key === 'Escape') {
      this.close({});
    }

    if (evt.key === 'ArrowDown') {
      if (!this.state.open) {
        evt.target.click();
      } else {
        options[Math.min(selectedOption + 1, options.length - 1)].focus();
      }
    }

    if (evt.key === 'ArrowUp') {
      options[Math.max(selectedOption - 1, 0)].focus();
    }

    if (/^\w$/.test(evt.key)) {
      const matchedOption = findLetterOption(
        options.slice(1),
        evt.key,
        options.indexOf(document.activeElement)
      );
      if (matchedOption) {
        matchedOption.focus();
      }
    }
  };

  render() {
    const {open, scrollable, menuStyle, listStyles} = this.state;
    const {icon, id, active, disabled, label, children, className, primary, main, small} =
      this.props;

    return (
      <div
        id={id}
        className={classnames(className, 'Dropdown', {
          'is-open': open,
        })}
        ref={this.storeContainer}
        onClick={this.toggleOpen}
        onKeyDown={this.handleKeyPress}
      >
        <Button
          icon={icon}
          primary={primary}
          main={main}
          small={small}
          className="activateButton"
          aria-haspopup="true"
          aria-expanded={open ? 'true' : 'false'}
          active={active || open}
          disabled={disabled}
          id={id ? id + '-button' : undefined}
        >
          <span>{label}</span>
          <Icon type="down" className="downIcon" />
        </Button>
        <div
          className="menu"
          aria-labelledby={id ? id + '-button' : ''}
          ref={this.menuContainer}
          style={menuStyle}
        >
          <DropdownOptionsList
            open={open}
            closeParent={() => this.close({})}
            className={classnames({scrollable})}
            style={listStyles}
          >
            {children}
          </DropdownOptionsList>
        </div>
      </div>
    );
  }

  storeContainer = (node) => {
    this.container = node;
  };

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
    document.body.removeEventListener('scroll', this.handleScroll, true);
    window.removeEventListener('resize', this.fixPositioning);
  }
}

Dropdown.Option = DropdownOption;
Dropdown.Submenu = Submenu;
