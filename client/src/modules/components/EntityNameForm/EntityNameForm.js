/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Input, Icon, ErrorMessage} from 'components';
import {Link} from 'react-router-dom';

import './EntityNameForm.scss';

export default class EntityNameForm extends Component {
  state = {
    name: this.props.initialName
  };

  inputRef = input => {
    this.nameInput = input;
  };

  componentDidMount() {
    if (this.nameInput && this.props.autofocus) {
      this.nameInput.focus();
      this.nameInput.select();
    }
  }

  updateName = evt => {
    this.setState({
      name: evt.target.value
    });
  };

  render() {
    const {id, entity} = this.props;
    const {name} = this.state;
    return (
      <div className="EntityNameForm head">
        <div className="name-container">
          <Input
            id="name"
            type="text"
            ref={this.inputRef}
            onChange={this.updateName}
            value={name || ''}
            className="name-input"
            placeholder={`${entity} name`}
            isInvalid={!name}
          />
          {!name && (
            <ErrorMessage className="warning">{`${entity}'s name can not be empty`}</ErrorMessage>
          )}
        </div>
        <div className="tools">
          <button
            className="Button tool-button save-button"
            disabled={!name || this.props.disabledButtons}
            onClick={evt => this.props.onSave(evt, name)}
          >
            <Icon type="check" />
            Save
          </button>
          <Link
            disabled={this.props.disabledButtons}
            className="Button tool-button cancel-button"
            to={`/${entity.toLowerCase()}/${id}`}
            onClick={this.props.onCancel}
          >
            <Icon type="stop" />
            Cancel
          </Link>
        </div>
      </div>
    );
  }
}
