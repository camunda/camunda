import React from 'react';
import classnames from 'classnames';

import DropdownOption from './DropdownOption';

import {Icon} from 'components';

import './Submenu.css';

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
    this.props.forceOpen(evt);
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
