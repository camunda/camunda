/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Icon, Select} from 'components';
import DropdownOption from './DropdownOption';
import Submenu from './Submenu';
import {findLetterOption} from './service';

import './Dropdown.scss';

export default class Dropdown extends React.Component {
  menuContainer = React.createRef();

  constructor(props) {
    super(props);
    this.initilizeHeaderAndFooterRefs();

    this.state = {
      open: false,
      openSubmenu: null,
      fixedSubmenu: null,
      menuStyle: {right: 0},
      listStyles: {},
    };
  }

  toggleOpen = (evt) => {
    evt.preventDefault();

    const {disabled, onOpen} = this.props;

    if (!disabled) {
      const newOpenState = !this.state.open;
      this.setState(
        {open: newOpenState, openSubmenu: null, fixedSubmenu: null},
        () => onOpen && onOpen(newOpenState)
      );
      this.calculateMenuStyle(newOpenState);
    }
  };

  close = ({target}) => {
    if (this.state.open && !this.container.contains(target)) {
      this.setState({open: false, openSubmenu: null});
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
  }

  initilizeHeaderAndFooterRefs() {
    if (!this.footerRef || !document.body.contains(this.footerRef)) {
      this.footerRef = document.body.querySelector('.Footer');
    }
    if (!this.headerRef || !document.body.contains(this.headerRef)) {
      this.headerRef = document.body.querySelector('.Header');
    }
  }

  calculateMenuStyle = (open) => {
    const activeButton = this.container.querySelector('.activateButton');
    const menuStyle = {minWidth: this.container.clientWidth + 'px'};
    const listStyles = {};
    let scrollable = false;
    const margin = 10;

    const bodyWidth = document.body.clientWidth;
    const overlay = this.menuContainer.current;
    const buttonPosition = activeButton.getBoundingClientRect();
    this.initilizeHeaderAndFooterRefs();
    const footerTop = this.footerRef?.getBoundingClientRect().top || window.innerHeight;
    const headerBottom = this.headerRef?.getBoundingClientRect().bottom || 0;

    const offsetParent = activeButton.offsetParent.getBoundingClientRect();

    if (open) {
      menuStyle.top = buttonPosition.top - offsetParent.top + activeButton.offsetHeight;
    }

    menuStyle.left = buttonPosition.left - offsetParent.left;

    // check to flip menu horizentally
    if (buttonPosition.left + overlay.clientWidth > bodyWidth) {
      menuStyle.left -= overlay.clientWidth - buttonPosition.width;
    }

    if (open && buttonPosition.bottom + overlay.clientHeight > footerTop) {
      scrollable = true;
      listStyles.height = footerTop - buttonPosition.bottom - margin;

      // check to flip menu vertically
      if (buttonPosition.bottom + overlay.clientHeight > footerTop) {
        menuStyle.top -= overlay.clientHeight + buttonPosition.height + 6; // 2 x 3px menu margin
        if (buttonPosition.top - headerBottom >= overlay.clientHeight) {
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

    if (evt.key === 'ArrowRight') {
      if (options[selectedOption].classList.contains('Submenu')) {
        this.setState(
          {
            fixedSubmenu: [...this.container.querySelectorAll('li > *')].indexOf(
              options[selectedOption]
            ),
          },
          () => {
            const childElement = document.activeElement.querySelector('[tabindex="0"]');
            if (childElement) {
              childElement.focus();
            }
          }
        );
      }
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
    const {icon, id, active, disabled, label, children, className, primary, main} = this.props;

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
          className="activateButton"
          aria-haspopup="true"
          aria-expanded={open ? 'true' : 'false'}
          active={active || open}
          disabled={disabled}
          id={id ? id + '-button' : undefined}
        >
          {label}
          <Icon type="down" className="downIcon" />
        </Button>
        <div
          className="menu"
          aria-labelledby={id ? id + '-button' : ''}
          ref={this.menuContainer}
          style={menuStyle}
        >
          <ul className={classnames({scrollable})} style={listStyles}>
            {React.Children.map(children, (child, idx) => (
              <li key={idx}>
                {child && (child.type === Submenu || child.type === Select.Submenu)
                  ? React.cloneElement(child, {
                      open:
                        this.state.fixedSubmenu === idx ||
                        (this.state.fixedSubmenu === null && this.state.openSubmenu === idx),
                      offset: this.menuContainer.current && this.menuContainer.current.offsetWidth,
                      setOpened: () => {
                        this.setState({openSubmenu: idx});
                      },
                      setClosed: () => {
                        this.setState({openSubmenu: null});
                      },
                      forceToggle: (evt) => {
                        evt.stopPropagation();
                        evt.preventDefault();
                        this.setState(({fixedSubmenu}) => {
                          return {fixedSubmenu: fixedSubmenu === idx ? null : idx};
                        });
                      },
                      closeParent: () => this.setState({open: false, openSubmenu: null}),
                    })
                  : child}
              </li>
            ))}
          </ul>
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
  }
}

Dropdown.Option = DropdownOption;
Dropdown.Submenu = Submenu;
