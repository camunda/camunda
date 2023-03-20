/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {CSSProperties, KeyboardEvent, MouseEvent, ReactNode} from 'react';
import classnames from 'classnames';

import {Button, Icon} from 'components';
import {getScreenBounds} from 'services';

import DropdownOption from './DropdownOption';
import Submenu from './Submenu';
import DropdownOptionsList from './DropdownOptionsList';
import {findLetterOption} from './service';

import './Dropdown.scss';

type DropdownProps = {
  disabled?: boolean;
  onOpen?: (open: boolean) => void;
  icon?: boolean;
  id?: string;
  active?: boolean;
  label: string | JSX.Element[];
  children?: ReactNode;
  className?: string;
  primary?: boolean;
  main?: boolean;
  small?: boolean;
};

type DropdownState = {
  open: boolean;
  menuStyle: CSSProperties;
  listStyles: CSSProperties;
  scrollable?: boolean;
};

export default class Dropdown extends React.Component<DropdownProps, DropdownState> {
  menuContainer = React.createRef<HTMLDivElement>();
  container: HTMLElement | null = null;

  constructor(props: DropdownProps) {
    super(props);

    this.state = {
      open: false,
      menuStyle: {right: 0},
      listStyles: {},
    };
  }

  toggleOpen = (evt: MouseEvent<HTMLDivElement>) => {
    evt.preventDefault();

    const {disabled, onOpen} = this.props;

    if (!disabled) {
      const newOpenState = !this.state.open;
      this.setState({open: newOpenState}, () => onOpen?.(newOpenState));
      this.calculateMenuStyle(newOpenState);
    }
  };

  close = (evt: {target: EventTarget | null} | {}) => {
    if (
      this.state.open &&
      !('target' in evt && this.container?.contains(evt.target as HTMLElement | null))
    ) {
      this.setState({open: false});
      this.calculateMenuStyle(false);
    }
  };

  handleScroll = (evt: {target: EventTarget | null} | {}) => {
    if ('target' in evt && (evt.target as HTMLElement | null)?.contains(this.container)) {
      this.close({});
    }
  };

  componentDidMount() {
    document.body.addEventListener('click', this.close, true);
    document.body.addEventListener('scroll', this.handleScroll, true);
    this.container &&
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

  calculateMenuStyle = (open: boolean) => {
    const activeButton = this.container?.querySelector<HTMLButtonElement>('.activateButton');
    const menuStyle: CSSProperties = {minWidth: this.container?.clientWidth + 'px'};
    const listStyles: CSSProperties = {};
    let scrollable = false;
    const margin = 10;

    const bodyWidth = document.body.clientWidth;
    const overlay = this.menuContainer.current;
    if (activeButton) {
      const buttonPosition = activeButton?.getBoundingClientRect();
      const screenBounds = getScreenBounds();
      const offsetParent = activeButton?.offsetParent?.getBoundingClientRect();

      if (offsetParent) {
        if (open) {
          menuStyle.top = buttonPosition.top - offsetParent.top + activeButton.offsetHeight;
        }

        menuStyle.left = buttonPosition.left - offsetParent.left;
      }
      if (overlay) {
        // check to flip menu horizentally
        if (
          buttonPosition.left + overlay.clientWidth > bodyWidth &&
          typeof menuStyle.left === 'number'
        ) {
          menuStyle.left -= overlay.clientWidth - buttonPosition.width;
        }

        if (open && buttonPosition.bottom + overlay.clientHeight > screenBounds.bottom) {
          scrollable = true;
          listStyles.height = screenBounds.bottom - buttonPosition.bottom - margin;

          // check to flip menu vertically
          if (
            buttonPosition.bottom + overlay.clientHeight > screenBounds.bottom &&
            typeof menuStyle.top === 'number'
          ) {
            menuStyle.top -= overlay.clientHeight + buttonPosition.height + 6; // 2 x 3px menu margin
            if (buttonPosition.top - screenBounds.top >= overlay.clientHeight) {
              scrollable = false;
              listStyles.height = 'auto';
            }
          }
        }
      }
    }

    this.setState({menuStyle, listStyles, scrollable});
  };

  handleKeyPress = (evt: KeyboardEvent<HTMLDivElement>) => {
    evt.stopPropagation();

    const options = Array.from(
      this.container?.querySelectorAll<HTMLElement>('.activateButton, li > :not([disabled])') || []
    );

    evt = evt || window.event;
    const eventTarget = evt.target as HTMLDivElement | null;
    const activeElement = document.activeElement as HTMLDivElement | null;
    const selectedOption = activeElement ? options.indexOf(activeElement) : null;

    if (evt.key !== 'Tab') {
      evt.preventDefault();
    }

    if (evt.key === 'Enter') {
      eventTarget?.click();
    }

    if (evt.key === 'Escape') {
      console.log('first');
      this.close({});
    }

    if (evt.key === 'ArrowDown') {
      if (!this.state.open) {
        eventTarget?.click();
      } else if (selectedOption !== null) {
        options[Math.min(selectedOption + 1, options.length - 1)]?.focus();
      }
    }

    if (evt.key === 'ArrowUp' && selectedOption !== null) {
      options[Math.max(selectedOption - 1, 0)]?.focus();
    }

    if (/^\w$/.test(evt.key) && activeElement) {
      const matchedOption = findLetterOption(
        options.slice(1),
        evt.key,
        options.indexOf(activeElement)
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

  storeContainer = (node: HTMLDivElement) => {
    this.container = node;
  };

  componentWillUnmount() {
    document.body.removeEventListener('click', this.close, true);
    document.body.removeEventListener('scroll', this.handleScroll, true);
    window.removeEventListener('resize', this.fixPositioning);
  }

  static Option = DropdownOption;
  static Submenu = Submenu;
}
