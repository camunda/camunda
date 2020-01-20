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
      listStyles: {}
    };
  }

  toggleOpen = evt => {
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

  componentDidMount() {
    document.body.addEventListener('click', this.close, true);
  }

  initilizeHeaderAndFooterRefs() {
    if (!this.footerRef || !document.body.contains(this.footerRef)) {
      this.footerRef = document.body.querySelector('.Footer');
    }
    if (!this.headerRef || !document.body.contains(this.headerRef)) {
      this.headerRef = document.body.querySelector('.Header');
    }
  }

  calculateMenuStyle = open => {
    const activeButton = this.container.querySelector('.activateButton');
    const menuStyle = {minWidth: this.container.clientWidth + 'px'};
    const listStyles = {};
    let scrollable = false;
    const margin = 10;

    const bodyWidth = document.body.clientWidth;
    const overlay = this.menuContainer.current;
    const buttonPosition = activeButton.getBoundingClientRect();
    this.initilizeHeaderAndFooterRefs();
    const footerTop = this.footerRef.getBoundingClientRect().top;
    const headerBottom = this.headerRef.getBoundingClientRect().bottom;

    // check to flip menu horizentally
    if (buttonPosition.left + overlay.clientWidth > bodyWidth) {
      menuStyle.right = 0;
    } else {
      menuStyle.left = 0;
    }

    if (open && buttonPosition.bottom + overlay.clientHeight > footerTop) {
      const oneItemHeight = overlay.querySelector('li').clientHeight;
      const fixedListHeight = this.props.fixedOptions
        ? this.props.fixedOptions.length * oneItemHeight
        : 0;

      scrollable = true;
      listStyles.height = footerTop - buttonPosition.bottom - fixedListHeight - margin;

      // check to flip menu vertically
      if (buttonPosition.bottom + oneItemHeight * 4 + fixedListHeight > footerTop) {
        menuStyle.bottom = activeButton.offsetHeight;

        if (buttonPosition.top - headerBottom >= overlay.clientHeight) {
          scrollable = false;
          listStyles.height = 'auto';
        }
      }
    }

    this.setState({menuStyle, listStyles, scrollable});
  };

  handleKeyPress = evt => {
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
            )
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

    return (
      <div
        id={this.props.id}
        className={classnames(this.props.className, 'Dropdown', {
          'is-open': open
        })}
        ref={this.storeContainer}
        onClick={this.toggleOpen}
        onKeyDown={this.handleKeyPress}
      >
        <Button
          color={this.props.color}
          variant={this.props.color ? 'primary' : null}
          className="activateButton"
          aria-haspopup="true"
          aria-expanded={open ? 'true' : 'false'}
          active={this.props.active || open}
          disabled={this.props.disabled}
          id={this.props.id ? this.props.id + '-button' : undefined}
        >
          {this.props.label}
          <Icon type="down" className="downIcon" />
        </Button>
        <div
          className="menu"
          aria-labelledby={this.props.id ? this.props.id + '-button' : ''}
          ref={this.menuContainer}
          style={menuStyle}
        >
          <ul className={classnames({scrollable})} style={listStyles}>
            {React.Children.map(this.props.children, (child, idx) => (
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
                      forceToggle: evt => {
                        evt.stopPropagation();
                        evt.preventDefault();
                        this.setState(({fixedSubmenu}) => {
                          return {fixedSubmenu: fixedSubmenu === idx ? null : idx};
                        });
                      },
                      closeParent: () => this.setState({open: false, openSubmenu: null})
                    })
                  : child}
              </li>
            ))}
          </ul>
          {this.props.fixedOptions && (
            <ul className="fixedList">
              {this.props.fixedOptions.map((item, idx) => (
                <li key={idx}>{item}</li>
              ))}
            </ul>
          )}
        </div>
      </div>
    );
  }

  storeContainer = node => {
    this.container = node;
  };

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
  }
}

Dropdown.Option = DropdownOption;
Dropdown.Submenu = Submenu;
