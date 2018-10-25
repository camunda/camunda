import React from 'react';
import classnames from 'classnames';

import DropdownOption from './DropdownOption';

import {Icon} from 'components';

import './Submenu.scss';

export default class Submenu extends React.Component {
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

  render() {
    return (
      <DropdownOption
        checked={this.props.checked}
        disabled={this.props.disabled}
        className={classnames('Submenu', {
          open: this.props.open
        })}
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
            style={{left: this.props.offset - 1 + 'px'}}
          >
            {this.props.children}
          </div>
        )}
      </DropdownOption>
    );
  }
}
