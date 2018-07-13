import React from 'react';
import {Modal, Button, ControlGroup, Typeahead} from 'components';

import {BooleanInput} from './boolean';
import {NumberInput} from './number';
import {StringInput} from './string';
import {DateInput} from './date';

import {loadVariables} from './service';

import './VariableFilter.css';

export default class VariableFilter extends React.Component {
  state = {
    valid: false,
    filter: {},
    selectedVariable: null
  };

  componentDidMount = async () => {
    if (this.props.filterData) {
      const filterData = this.props.filterData[0].data;

      const InputComponent = this.getInputComponentForVariable(filterData);
      const filter = InputComponent.parseFilter
        ? InputComponent.parseFilter(this.props.filterData)
        : {operator: filterData.operator, values: filterData.values};

      this.setState({
        selectedVariable: {name: filterData.name, type: filterData.type},
        filter,
        valid: true
      });
    }
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

    switch (variable.type) {
      case 'String':
        return StringInput;
      case 'Boolean':
        return BooleanInput;
      case 'Date':
        return DateInput;
      default:
        return NumberInput;
    }
  };

  setValid = valid => this.setState({valid});

  changeFilter = filter => this.setState({filter});

  render() {
    const {selectedVariable} = this.state;

    const ValueInput = this.getInputComponentForVariable(selectedVariable);

    return (
      <Modal open={true} onClose={this.props.close} className="VariableFilter__modal">
        <Modal.Header>Add Variable Filter</Modal.Header>
        <Modal.Content>
          <form>
            <ControlGroup layout="horizontal">
              <label htmlFor="VariableFilter__variables">Variable Name</label>
              <Typeahead
                initialValue={this.getVariableName(selectedVariable)}
                getValues={loadVariables(
                  this.props.processDefinitionKey,
                  this.props.processDefinitionVersion
                )}
                selectValue={this.selectVariable}
                nameRenderer={this.getVariableName}
              />
            </ControlGroup>
            <ValueInput
              processDefinitionKey={this.props.processDefinitionKey}
              processDefinitionVersion={this.props.processDefinitionVersion}
              variable={selectedVariable}
              setValid={this.setValid}
              changeFilter={this.changeFilter}
              filter={this.state.filter}
            />
          </form>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            type="primary"
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
          type: 'variable',
          data: {
            name: variable.name,
            type: variable.type,
            ...this.state.filter
          }
        });
  };
}
