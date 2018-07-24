import React from 'react';
import classnames from 'classnames';

import DropdownOption from './DropdownOption';

import './Submenu.css';

export default class Submenu extends React.Component {
  open = evt => {
    if (this.props.onClick) {
      this.props.onClick(evt);
    }
    this.props.onOpen(evt);
  };

  render() {
    return (
      <React.Fragment>
        <DropdownOption
          checked={this.props.checked}
          disabled={this.props.disabled}
          className={classnames('Submenu__DropdownOption', {
            'Submenu__DropdownOption--open': this.props.open
          })}
          onClick={this.open}
        >
          {this.props.label}
          <div className="Submenu__indicator" />
        </DropdownOption>
        {this.props.open && (
          <div className="Submenu__container" style={{left: this.props.offset + 'px'}}>
            {this.props.children}
          </div>
        )}
      </React.Fragment>
    );
  }
}
