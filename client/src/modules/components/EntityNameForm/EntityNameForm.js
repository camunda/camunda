/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {Input, Icon, Button} from 'components';
import {withErrorHandling} from 'HOC';

import './EntityNameForm.scss';
import {t} from 'translation';

export class EntityNameForm extends React.Component {
  state = {
    loading: false
  };

  nameInput = React.createRef();

  componentDidMount() {
    const input = this.nameInput.current;
    if (input && this.props.isNew) {
      input.focus();
      input.select();
    }
  }

  render() {
    const {entity, name, isNew, onCancel, onSave, onChange, children} = this.props;
    const {loading} = this.state;

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
            main
            className="tool-button save-button"
            disabled={!name || loading}
            onClick={async () => {
              this.setState({loading: true});
              this.props.mightFail(onSave(), () => this.setState({loading: false}));
            }}
          >
            <Icon type="check" />
            {t('common.save')}
          </Button>
          <Link
            disabled={loading}
            className="Button main tool-button cancel-button"
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

export default withErrorHandling(EntityNameForm);
