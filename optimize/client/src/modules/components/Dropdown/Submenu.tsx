/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Component,
  createRef,
  CSSProperties,
  KeyboardEvent,
  MouseEvent,
  ReactNode,
  RefObject,
  UIEvent,
} from 'react';
import classnames from 'classnames';

import {getScreenBounds} from 'services';

import DropdownOption from './DropdownOption';
import {findLetterOption} from './service';

import {Icon} from 'components';

import './Submenu.scss';

export type SubmenuProps = {
  label?: string | JSX.Element[];
  open?: boolean;
  checked?: boolean;
  fixed?: boolean;
  disabled?: boolean;
  openToLeft?: boolean;
  offset?: number;
  children?: ReactNode;
  onClick?: (evt: MouseEvent<HTMLDivElement>) => void;
  onOpen?: (evt: UIEvent<HTMLElement>) => void;
  setOpened?: (evt: UIEvent<HTMLElement>) => void;
  setClosed?: (evt: UIEvent<HTMLElement>) => void;
  closeParent?: (evt: MouseEvent<HTMLDivElement>) => void;
  onMenuMouseEnter?: (evt: MouseEvent<HTMLDivElement>) => void;
  onMenuMouseLeave?: (evt: MouseEvent<HTMLDivElement>) => void;
  forceToggle?: (evt: UIEvent<HTMLDivElement>) => void;
  onClose?: () => void;
};

type SubmenuState = {
  styles: CSSProperties;
  scrollable: boolean;
};

export default class Submenu extends Component<SubmenuProps, SubmenuState> {
  containerRef: RefObject<HTMLDivElement>;
  menuObserver: MutationObserver;

  constructor(props: SubmenuProps) {
    super(props);

    this.containerRef = createRef<HTMLDivElement>();
    this.menuObserver = new MutationObserver(this.calculatePlacement);

    this.state = {styles: {}, scrollable: false};
  }

  onClick = (evt: MouseEvent<HTMLDivElement>) => {
    if (this.props.disabled) {
      return;
    }

    this.props.onClick?.(evt);
    this.props.onOpen?.(evt);
    this.props.forceToggle?.(evt);
  };

  onMouseOver = (evt: UIEvent<HTMLElement>) => {
    if (this.props.disabled || this.props.open) {
      return;
    }

    this.props.onOpen?.(evt);
    this.props.setOpened?.(evt);
  };

  onMouseLeave = (evt: UIEvent<HTMLElement>) => {
    if (this.props.disabled) {
      return;
    }
    this.props.setClosed?.(evt);
  };

  onKeyDown = (evt: KeyboardEvent<HTMLDivElement>) => {
    const eventTarget = evt.target as HTMLElement;
    const activeElement = document.activeElement as
      | (HTMLDivElement & {
          parentNode?: HTMLElement;
          nextElementSibling: HTMLElement;
          previousElementSibling: HTMLElement;
        })
      | undefined;
    evt.stopPropagation();

    if (evt.key !== 'Tab') {
      evt.preventDefault();
    }

    if (evt.key === 'Enter') {
      eventTarget.click();
    }

    if (evt.key === 'Escape' || evt.key === this.getCloseDirection()) {
      activeElement?.parentNode?.closest<HTMLElement>('.DropdownOption')?.focus();
      this.props.forceToggle?.(evt);
    }

    if (evt.key === 'ArrowDown') {
      const next = activeElement?.nextElementSibling;
      next?.focus();
    }

    if (evt.key === 'ArrowUp') {
      const previous = activeElement?.previousElementSibling;
      previous?.focus();
    }

    if (String.fromCharCode(evt.keyCode).match(/(\w)/g)) {
      const options = Array.from(
        this.containerRef.current?.querySelectorAll<HTMLElement>('.DropdownOption') || []
      );

      if (activeElement) {
        const matchedOption = findLetterOption(
          options,
          evt.key,
          options.indexOf(activeElement) + 1
        );

        matchedOption?.focus();
      }
    }
  };

  componentDidMount() {
    this.containerRef.current &&
      this.menuObserver.observe(this.containerRef.current, {
        childList: true,
        subtree: true,
      });
  }

  componentWillUnmount() {
    this.menuObserver.disconnect();
  }

  componentDidUpdate(prevProps: SubmenuProps) {
    if (!prevProps.open && this.props.open) {
      document.activeElement?.querySelector<HTMLElement>('[tabindex="0"]')?.focus();
    }

    if (prevProps.open && !this.props.open) {
      this.props.onClose?.();
    }
  }

  calculatePlacement = () => {
    const styles: CSSProperties = {};
    const container = this.containerRef.current;
    if (container) {
      const submenu = container.querySelector('.childrenContainer');
      if (submenu) {
        const parentMenu = container.getBoundingClientRect();
        const body = document.body;

        if (this.props.openToLeft || parentMenu.right + submenu.clientWidth > body.clientWidth) {
          styles.right = this.props.offset + 'px';
        } else {
          styles.left = this.props.offset + 'px';
        }

        const margin = 10;
        const screenBounds = getScreenBounds();

        const bottomAvailableHeight = screenBounds.bottom - parentMenu.top - margin;
        if (submenu.clientHeight > bottomAvailableHeight) {
          let shiftDistance = submenu.clientHeight - bottomAvailableHeight;

          const topAvailableHeight = parentMenu.top - screenBounds.top - margin;
          if (shiftDistance > topAvailableHeight) {
            shiftDistance = topAvailableHeight;
          }

          styles.top = '-' + shiftDistance + 'px';
          styles.maxHeight = screenBounds.bottom - screenBounds.top - 2 * margin;
        }
      }
    }
    this.setState({styles});
  };

  getOpenDirection = () => (this.props.openToLeft ? 'ArrowLeft' : 'ArrowRight');
  getCloseDirection = () => (this.props.openToLeft ? 'ArrowRight' : 'ArrowLeft');

  render() {
    return (
      <DropdownOption
        checked={this.props.checked}
        disabled={this.props.disabled}
        className={classnames('Submenu', {
          open: this.props.open,
          fixed: this.props.fixed,
          leftCheckMark: this.props.openToLeft,
        })}
        ref={this.containerRef}
        onClick={this.onClick}
        onMouseOver={this.onMouseOver}
        onMouseLeave={this.onMouseLeave}
        onKeyDown={(evt) => {
          if (evt.key === this.getOpenDirection() && !this.props.disabled) {
            this.props.forceToggle?.(evt);
          }
        }}
      >
        {this.props.label}
        <Icon
          type={this.props.openToLeft ? 'left' : 'right'}
          className={classnames('submenuArrow', {left: this.props.openToLeft})}
        />
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
