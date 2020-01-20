/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Input, Message} from 'components';

import './DateInput.scss';
import {t} from 'translation';

class DateInput extends React.PureComponent {
  render() {
    return (
      <div className="DateInput">
        <Input
          type="text"
          ref={this.props.reference}
          className={this.props.className}
          value={this.props.date}
          onFocus={this.props.onFocus}
          onClick={this.onClick}
          onKeyDown={this.onKeyDown}
          onChange={this.onInputChange}
          isInvalid={this.props.error}
          disabled={this.props.disabled}
        />
        {this.props.icon}
        {this.props.error && (
          <Message error className="DateInput__warning">
            {t('common.filter.dateModal.invalidDate')}
          </Message>
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
