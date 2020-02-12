/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Input, Icon, Button} from 'components';
import {Link} from 'react-router-dom';

import './EntityNameForm.scss';
import {t} from 'translation';

export default class EntityNameForm extends React.Component {
  nameInput = React.createRef();

  componentDidMount() {
    const input = this.nameInput.current;
    if (input && this.props.isNew) {
      input.focus();
      input.select();
    }
  }

  render() {
    const {entity, name, isNew, disabledButtons, onCancel, onSave, onChange, children} = this.props;

    const homeLink = entity === 'Process' ? '../' : '../../';

    return (
      <div className="EntityNameForm head">
        <div className="name-container">
          <Input
            id="name"
            type="text"
            ref={this.nameInput}
            onChange={onChange}
            value={name || ''}
            className="name-input"
            placeholder={t(`common.entity.namePlaceholder.${entity}`)}
            autoComplete="off"
          />
        </div>
        <div className="tools">
          {children}
          <Button
            className="tool-button save-button"
            disabled={!name || disabledButtons}
            onClick={onSave}
          >
            <Icon type="check" />
            {t('common.save')}
          </Button>
          <Link
            disabled={disabledButtons}
            className="Button tool-button cancel-button"
            to={isNew ? homeLink : './'}
            onClick={onCancel}
          >
            <Icon type="stop" />
            {t('common.cancel')}
          </Link>
        </div>
      </div>
    );
  }
}
