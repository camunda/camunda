/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Input, ErrorMessage} from 'components';

import './DateInput.scss';

class DateInput extends React.PureComponent {
  render() {
    return (
      <div className="DateInput__input-group">
        <Input
          type="text"
          ref={this.props.reference}
          className={classnames(this.props.className, 'DateInput')}
          value={this.props.date}
          onFocus={this.props.onFocus}
          onClick={this.onClick}
          onKeyDown={this.onKeyDown}
          onChange={this.onInputChange}
          isInvalid={this.props.error}
        />
        {this.props.error && (
          <ErrorMessage className="DateInput__warning">Please enter a valid date</ErrorMessage>
        )}
      </div>
    );
  }

  onClick = event => {
    // onClick property is optional, so there need to be safeguard
    if (typeof this.props.onClick === 'function') {
      this.props.onClick(event);
    }
  };

  onKeyDown = ({key}) => {
    if (key === 'Enter') {
      this.props.onSubmit();
    }
  };

  onInputChange = event => {
    const value = event.target.value;
    this.props.onDateChange(value);
  };
}

export default React.forwardRef((props, ref) => <DateInput {...props} reference={ref} />);
