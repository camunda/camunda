/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Icon} from 'components';
import DropdownOption from './DropdownOption';
import Submenu from './Submenu';

import './Dropdown.scss';

export default class Dropdown extends React.Component {
  menuContainer = React.createRef();

  constructor(props) {
    super(props);
    this.options = [];
    this.footerRef = document.body.querySelector('.Footer');

    this.state = {
      open: false,
      openSubmenu: null,
      fixedSubmenu: null,
      menuStyle: {},
      listStyles: {}
    };
  }

  toggleOpen = evt => {
    evt.preventDefault();
    if (!this.props.disabled) {
      this.setState({open: !this.state.open, openSubmenu: null, fixedSubmenu: null});
      this.calculateMenuStyle(this.state.open);
    }
  };

  close = ({target}) => {
    if (this.state.open && !this.container.contains(target)) {
      this.setState({open: false, openSubmenu: null});
      this.calculateMenuStyle(true);
    }
  };

  componentDidMount() {
    document.body.addEventListener('click', this.close, true);
    window.addEventListener('resize', this.calculateMenuStyle);
  }

  calculateMenuStyle = isOpen => {
    const container = this.container;
    const activeButton = container.querySelector('.activateButton');
    const footerTop = this.footerRef.getBoundingClientRect().top;
    const menuStyle = {minWidth: container.clientWidth + 'px'};
    const listStyles = {};
    let scrollable = false;

    const bodyWidth = document.body.clientWidth;
    const overlay = this.menuContainer.current;
    const buttonPosition = activeButton.getBoundingClientRect();

    if (buttonPosition.left + overlay.clientWidth > bodyWidth) {
      menuStyle.right = 0;
    } else {
      menuStyle.left = 0;
    }

    if (!isOpen && buttonPosition.bottom + overlay.clientHeight > footerTop) {
      const listItemsCount = React.Children.count(this.props.children);
      const oneItemHeight = this.options[0].clientHeight;
      const fixedListHeight = this.props.fixedOptions
        ? this.props.fixedOptions.length * oneItemHeight
        : 0;

      scrollable = true;
      listStyles.height = oneItemHeight * listItemsCount;
      if (listItemsCount > 4) {
        listStyles.height = oneItemHeight * 4;
      }

      if (buttonPosition.bottom + listStyles.height + fixedListHeight > footerTop) {
        menuStyle.bottom = activeButton.offsetHeight;
      }
    }

    this.setState({menuStyle, listStyles, scrollable});
  };

  handleKeyPress = evt => {
    const dropdownButton = this.container.children[0];

    const options = this.options.filter(
      option => option && option.getAttribute('disabled') === null
    );

    if (options[0] !== dropdownButton) {
      options.unshift(dropdownButton);
    }

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
        this.setState({fixedSubmenu: this.options.indexOf(document.activeElement)}, () => {
          const childElement = document.activeElement.querySelector('[tabindex="0"]');
          if (childElement) {
            childElement.focus();
          }
        });
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
  };

  render() {
    const {open, scrollable, listStyles} = this.state;

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
          className="activateButton"
          aria-haspopup="true"
          aria-expanded={open ? 'true' : 'false'}
          active={this.props.active}
          disabled={this.props.disabled}
          id={this.props.id ? this.props.id + '-button' : ''}
        >
          {this.props.label}
          <Icon type="down" className="downIcon" />
        </Button>
        <div
          className={classnames('menu')}
          aria-labelledby={this.props.id ? this.props.id + '-button' : ''}
          ref={this.menuContainer}
          style={this.state.menuStyle}
        >
          <ul className={classnames({scrollable})} style={listStyles}>
            {React.Children.map(this.props.children, (child, idx) => (
              <li ref={this.optionRef} key={idx}>
                {child && child.type === Submenu
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
          <ul className="fixedList">
            {this.props.fixedOptions &&
              this.props.fixedOptions.map((item, idx) => (
                <li ref={this.optionRef} key={idx}>
                  {item}
                </li>
              ))}
          </ul>
        </div>
      </div>
    );
  }

  optionRef = option => {
    if (option) {
      this.options.push(option.children[0]);
    }
  };

  storeContainer = node => {
    this.container = node;
  };

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
    window.removeEventListener('resize', this.calculateMenuStyle);
  }
}

Dropdown.Option = DropdownOption;
Dropdown.Submenu = Submenu;
