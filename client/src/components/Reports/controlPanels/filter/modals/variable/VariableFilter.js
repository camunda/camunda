/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Modal, Button, Typeahead, Labeled} from 'components';

import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

import {formatters} from 'services';

import './VariableFilter.scss';

export default class VariableFilter extends React.Component {
  state = {
    valid: false,
    filter: {},
    variables: [],
    selectedVariable: null
  };

  componentDidMount = async () => {
    if (this.props.filterData) {
      const filterData = this.props.filterData.data;

      const InputComponent = this.getInputComponentForVariable(filterData);
      const filter = InputComponent.parseFilter
        ? InputComponent.parseFilter(this.props.filterData)
        : filterData.data;

      this.setState({
        selectedVariable: {id: filterData.id, name: filterData.name, type: filterData.type},
        filter,
        valid: true
      });
    }

    this.setState({
      variables: await this.props.config.getVariables()
    });
  };

  selectVariable = async variable => {
    this.setState({
      selectedVariable: variable,
      filter: this.getInputComponentForVariable(variable).defaultFilter
    });
  };

  getInputComponentForVariable = variable => {
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

  setValid = valid => this.setState({valid});

  changeFilter = filter => this.setState({filter});

  render() {
    const {selectedVariable, variables} = this.state;

    const ValueInput = this.getInputComponentForVariable(selectedVariable);

    return (
      <Modal open={true} onClose={this.props.close} className="VariableFilter__modal">
        <Modal.Header>Add {formatters.camelCaseToLabel(this.props.filterType)} Filter</Modal.Header>
        <Modal.Content>
          <Labeled className="LabeledTypeahead" label="Variable Name">
            <Typeahead
              initialValue={selectedVariable}
              values={variables}
              onSelect={this.selectVariable}
              formatter={this.getVariableName}
              placeholder="Select or search for a variable here"
              label="variables"
            />
          </Labeled>
          <ValueInput
            config={this.props.config}
            variable={selectedVariable}
            setValid={this.setValid}
            changeFilter={this.changeFilter}
            filter={this.state.filter}
          />
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            variant="primary"
            color="blue"
            disabled={!this.state.valid}
            onClick={this.createFilter}
          >
            {this.props.filterData ? 'Edit ' : 'Add '}Filter
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  getVariableName = variable => (variable ? variable.name : null);

  createFilter = evt => {
    evt.preventDefault();

    const variable = this.state.selectedVariable;
    const InputComponent = this.getInputComponentForVariable(variable);

    InputComponent.addFilter
      ? InputComponent.addFilter(this.props.addFilter, variable, this.state.filter)
      : this.props.addFilter({
          type: this.props.filterType,
          data: {
            name: variable.id || variable.name,
            type: variable.type,
            data: this.state.filter
          }
        });
  };
}
