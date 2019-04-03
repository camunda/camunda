/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Input, Button} from 'components';

import './CopyToClipboard.scss';

export default class CopyToClipboard extends React.Component {
  copyText = event => {
    event.preventDefault();
    this.inputElement.select();
    document.execCommand('Copy');
  };

  storeInputElement = inputElement => {
    this.inputElement = inputElement;
  };

  render() {
    return (
      <div className={classnames('CopyToClipboard', this.props.className)}>
        <Input
          ref={this.storeInputElement}
          className="CopyToClipboard__input"
          readOnly
          disabled={this.props.disabled}
          value={this.props.value}
        />
        <Button
          className="CopyToClipboard__button"
          onClick={this.copyText}
          disabled={this.props.disabled}
        >
          Copy
        </Button>
      </div>
    );
  }
}
