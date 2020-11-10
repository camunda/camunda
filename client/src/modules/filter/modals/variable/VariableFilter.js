/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import {Modal, Button, Typeahead, Labeled} from 'components';

import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

import './VariableFilter.scss';
import {t} from 'translation';

export default class VariableFilter extends React.Component {
  state = {
    valid: false,
    filter: {},
    variables: [],
    selectedVariable: null,
  };

  componentDidMount = async () => {
    if (this.props.filterData) {
      const filterData = this.props.filterData.data;

      const InputComponent = this.getInputComponentForVariable(filterData);
      const filter = InputComponent.parseFilter
        ? InputComponent.parseFilter(this.props.filterData)
        : filterData.data;

      const {id, name, type} = filterData;
      this.setState({
        selectedVariable: {id, name, type},
        filter,
        valid: true,
      });
    }

    this.setState({
      variables: await this.props.config.getVariables(),
    });
  };

  selectVariable = (nameOrId) => {
    const variable = this.state.variables.find((variable) => this.getId(variable) === nameOrId);
    this.setState({
      selectedVariable: variable,
      filter: this.getInputComponentForVariable(variable).defaultFilter,
    });
  };

  getInputComponentForVariable = (variable) => {
    if (!variable) {
      return () => null;
    }

    switch (variable.type.toLowerCase()) {
      case 'string':
        return StringInput;
      case 'boolean':
        return BooleanInput;
      case 'date':
        return DateInput;
      default:
        return NumberInput;
    }
  };

  setValid = (valid) => this.setState({valid});

  changeFilter = (filter) => this.setState({filter});

  getId = (variable) => {
    if (variable) {
      return variable.id || variable.name;
    }
  };

  render() {
    const {selectedVariable, variables, filter, valid} = this.state;
    const {
      close,
      className,
      filterType,
      getPretext,
      getPosttext,
      config,
      filterData,
      forceEnabled,
    } = this.props;

    const ValueInput = this.getInputComponentForVariable(selectedVariable);

    return (
      <Modal open onClose={close} className={classnames('VariableFilter__modal', className)}>
        <Modal.Header>
          {t('common.filter.modalHeader', {
            type: t(`common.filter.types.${filterType}`),
          })}
        </Modal.Header>
        <Modal.Content>
          {getPretext?.(selectedVariable)}
          <Labeled className="LabeledTypeahead" label={t('common.filter.variableModal.inputLabel')}>
            <Typeahead
              onChange={this.selectVariable}
              initialValue={variables.length > 0 && this.getId(selectedVariable)}
              placeholder={t('common.filter.variableModal.inputPlaceholder')}
              noValuesMessage={t('common.filter.variableModal.noVariables')}
            >
              {variables.map((variable) => (
                <Typeahead.Option key={this.getId(variable)} value={this.getId(variable)}>
                  {this.getVariableName(variable)}
                </Typeahead.Option>
              ))}
            </Typeahead>
          </Labeled>
          <ValueInput
            config={config}
            variable={selectedVariable}
            setValid={this.setValid}
            changeFilter={this.changeFilter}
            filter={filter}
          />
          {getPosttext?.(selectedVariable)}
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={close}>
            {t('common.cancel')}
          </Button>
          <Button
            main
            primary
            disabled={!valid && !forceEnabled?.(selectedVariable)}
            onClick={this.createFilter}
          >
            {filterData ? t('common.filter.editFilter') : t('common.filter.addFilter')}
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  getVariableName = (variable) => (variable ? variable.name : null);

  createFilter = (evt) => {
    evt.preventDefault();

    const variable = this.state.selectedVariable;
    const InputComponent = this.getInputComponentForVariable(variable);
    const {filter} = this.state;
    const {addFilter, filterType} = this.props;

    InputComponent.addFilter
      ? InputComponent.addFilter(addFilter, filterType, variable, filter)
      : addFilter({
          type: filterType,
          data: {
            name: variable.id || variable.name,
            type: variable.type,
            data: filter,
          },
        });
  };
}
