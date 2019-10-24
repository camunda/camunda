/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Input, Icon} from 'components';
import {Link} from 'react-router-dom';

import './EntityNameForm.scss';
import {t} from 'translation';

export default class EntityNameForm extends Component {
  state = {
    name: this.props.initialName
  };

  inputRef = input => {
    this.nameInput = input;
  };

  componentDidMount() {
    if (this.nameInput && this.props.isNew) {
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
    const {entity, isNew} = this.props;
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
            placeholder={t(`common.entity.namePlaceholder.${entity}`)}
            autoComplete="off"
          />
        </div>
        <div className="tools">
          <button
            className="Button tool-button save-button"
            disabled={!name || this.props.disabledButtons}
            onClick={evt => this.props.onSave(evt, name)}
          >
            <Icon type="check" />
            {t('common.save')}
          </button>
          <Link
            disabled={this.props.disabledButtons}
            className="Button tool-button cancel-button"
            to={isNew ? '../../' : './'}
            onClick={this.props.onCancel}
          >
            <Icon type="stop" />
            {t('common.cancel')}
          </Link>
        </div>
      </div>
    );
  }
}
