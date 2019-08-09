/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ButtonGroup, Button} from 'components';
import {t} from 'translation';

export default class BooleanInput extends React.Component {
  static defaultFilter = {value: true};

  componentDidMount() {
    this.props.setValid(true);
  }

  setOperator = value => evt => {
    evt.preventDefault();
    this.props.changeFilter({value});
  };

  render() {
    return (
      <div className="VariableFilter__buttonRow">
        <ButtonGroup>
          <Button onClick={this.setOperator(true)} active={this.props.filter.value === true}>
            {t('common.filter.variableModal.bool.isTrue')}
          </Button>
          <Button onClick={this.setOperator(false)} active={this.props.filter.value === false}>
            {t('common.filter.variableModal.bool.isFalse')}
          </Button>
        </ButtonGroup>
      </div>
    );
  }
}
