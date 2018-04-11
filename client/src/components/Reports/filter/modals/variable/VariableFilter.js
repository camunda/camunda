import React from 'react';
import classnames from 'classnames';
import {Modal, Button, Select, Input, ControlGroup, ButtonGroup, ErrorMessage} from 'components';

import {loadVariables, loadValues} from './service';
import './VariableFilter.css';

const valuesToLoad = 20;

export default class VariableFilter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      variables: [],
      selectedVariableIdx: -1,
      operator: 'in',
      values: [],
      availableValues: [],
      valuesAreComplete: false,
      loading: false
    };
  }

  componentDidMount = async () => {
    const variables = await loadVariables(
      this.props.processDefinitionKey,
      this.props.processDefinitionVersion
    );

    if (this.props.filterData) {
      const filterData = this.props.filterData[0].data;
      this.setState({
        variables,
        selectedVariableIdx: variables.findIndex(v => v.name === filterData.name),
        operator: filterData.operator,
        values: filterData.values
      });
      this.loadAvailableValues({name: filterData.name, type: filterData.type});
    } else {
      this.setState({
        variables
      });
    }
  };

  loadAvailableValues = async ({name, type}) => {
    this.setState(
      {
        loading: true
      },
      async () => {
        const values = await loadValues(
          this.props.processDefinitionKey,
          this.props.processDefinitionVersion,
          name,
          type,
          0,
          this.state.availableValues.length + valuesToLoad + 1
        );

        const valuesAreComplete =
          values.length !== this.state.availableValues.length + valuesToLoad + 1;

        this.setState({
          availableValues: valuesAreComplete ? values : values.splice(0, values.length - 1),
          valuesAreComplete,
          loading: false
        });
      }
    );
  };

  loadMore = evt => {
    evt.preventDefault();
    this.loadAvailableValues(this.state.variables[this.state.selectedVariableIdx]);
  };

  selectVariable = ({target: {value}}) => {
    const variable = this.state.variables[value];

    let values = [''];
    if (variable.type === 'Boolean') {
      values = [true];
    }
    if (variable.type === 'String') {
      values = [];
    }

    this.setState(
      {
        selectedVariableIdx: value,
        operator: variable.type === 'Boolean' ? '=' : 'in',
        values,
        availableValues: [],
        valuesAreComplete: false
      },
      () => {
        if (variable.type === 'String') {
          this.loadAvailableValues(variable);
        }
      }
    );
  };

  render() {
    const {variables, selectedVariableIdx, operator, values, availableValues} = this.state;

    return (
      <Modal open={true} onClose={this.props.close} className="VariableFilter__modal">
        <Modal.Header>Add Variable Filter</Modal.Header>
        <Modal.Content>
          <form>
            <ControlGroup layout="horizontal">
              <label htmlFor="VariableFilter__variables">Variable Name</label>
              <Select
                value={selectedVariableIdx}
                onChange={this.selectVariable}
                name="VariableFilter__variables"
              >
                <Select.Option disabled value={-1}>
                  Please Select Variable
                </Select.Option>
                {variables.map(({name}, idx) => {
                  return (
                    <Select.Option value={idx} key={idx}>
                      {name}
                    </Select.Option>
                  );
                })}
              </Select>
            </ControlGroup>
            <div className="VariableFilter__buttonRow">
              <ButtonGroup className="VariableFilter__operatorButtons">
                {this.variableIsSelected() &&
                  this.renderOperatorButtons(variables[selectedVariableIdx].type, operator)}
              </ButtonGroup>
            </div>
            <div className="VariableFilter__valueFields">
              {this.variableIsSelected() &&
                this.renderValueFields(
                  variables[selectedVariableIdx].type,
                  availableValues,
                  values
                )}
            </div>
          </form>
        </Modal.Content>
        <Modal.Actions>
          <Button onClick={this.props.close}>Cancel</Button>
          <Button
            type="primary"
            color="blue"
            disabled={!this.selectionIsValid()}
            onClick={this.createFilter}
          >
            {this.props.filterData ? 'Edit ' : 'Add '}Filter
          </Button>
        </Modal.Actions>
      </Modal>
    );
  }

  selectionIsValid = () => {
    let isValid = true;
    const variable = this.state.variables[this.state.selectedVariableIdx];

    if (variable && this.typeIsNumeric(variable.type)) {
      // match float number: https://stackoverflow.com/a/10256077
      // match integer: https://stackoverflow.com/a/1779019
      const matcher = this.typeIsFloating(variable.type) ? /^[+-]?\d+(\.\d+)?$/ : /^[+-]?\d+?$/;
      const containsOnlyValidNumbers = this.state.values.every(value => matcher.test(value));
      isValid = isValid && containsOnlyValidNumbers;
    }

    isValid = isValid && this.variableIsSelected() && this.state.values.length > 0;
    return isValid;
  };

  typeIsNumeric = type => {
    return this.typeIsFloating(type) || this.typeIsInteger(type);
  };

  typeIsFloating = type => {
    return type === 'Double' || type === 'Float';
  };

  typeIsInteger = type => {
    return type === 'Short' || type === 'Integer' || type === 'Long';
  };

  variableIsSelected = () => {
    return this.state.selectedVariableIdx !== -1;
  };

  createFilter = evt => {
    evt.preventDefault();

    const variable = this.state.variables[this.state.selectedVariableIdx];

    let values;
    if (variable.type === 'String' || variable.type === 'Boolean') {
      values = this.state.values;
    } else {
      values = this.state.values.filter(value => value);
    }

    this.props.addFilter({
      type: 'variable',
      data: {
        name: variable.name,
        type: variable.type,
        operator: this.state.operator,
        values
      }
    });
  };

  selectOperator = evt => {
    evt.preventDefault();

    const changes = {
      operator: evt.target.getAttribute('operator')
    };

    const value = evt.target.getAttribute('value');
    if (value !== null) {
      // cast string value from DOM element to boolean
      changes.values = [value === 'true'];
    }

    this.setState(changes);
  };

  toggleValue = ({target: {checked, value}}) => {
    this.setState(prevState => {
      if (checked) {
        return {
          values: prevState.values.concat(value)
        };
      } else {
        return {
          values: prevState.values.filter(existingValue => existingValue !== value)
        };
      }
    });
  };

  changeValue = ({target}) => {
    this.setState(prevState => {
      const newValues = [...prevState.values];
      newValues[target.getAttribute('data-idx')] = target.value;

      return {
        values: newValues
      };
    });
  };

  renderOperatorButtons = (type, selection) => {
    switch (type) {
      case 'String':
        return [
          <Button
            key="="
            onClick={this.selectOperator}
            operator="in"
            className={classnames({'is-active': this.state.operator === 'in'})}
          >
            is
          </Button>,
          <Button
            key="!="
            onClick={this.selectOperator}
            operator="not in"
            className={classnames({'is-active': this.state.operator === 'not in'})}
          >
            is not
          </Button>
        ];
      case 'Boolean':
        return [
          <Button
            key="true"
            onClick={this.selectOperator}
            operator="="
            className={classnames({'is-active': this.state.values[0] === true})}
            value={true}
          >
            is true
          </Button>,
          <Button
            key="false"
            onClick={this.selectOperator}
            operator="="
            className={classnames({'is-active': this.state.values[0] === false})}
            value={false}
          >
            is false
          </Button>
        ];
      default:
        return [
          <Button
            key="="
            onClick={this.selectOperator}
            className={classnames({'is-active': this.state.operator === 'in'})}
            operator="in"
          >
            is
          </Button>,
          <Button
            key="!="
            onClick={this.selectOperator}
            className={classnames({'is-active': this.state.operator === 'not in'})}
            operator="not in"
          >
            is not
          </Button>,
          <Button
            key="<"
            onClick={this.selectOperator}
            className={classnames({'is-active': this.state.operator === '<'})}
            operator="<"
          >
            is less than
          </Button>,
          <Button
            key=">"
            onClick={this.selectOperator}
            className={classnames({'is-active': this.state.operator === '>'})}
            operator=">"
          >
            is greater than
          </Button>
        ];
    }
  };

  renderValueFields = (type, availableValues, values) => {
    switch (type) {
      case 'String':
        if (availableValues.length === 0) {
          return 'loading...';
        }
        return (
          <ul className="VariableFilter__valueList">
            {availableValues.map((value, idx) => {
              return (
                <li key={idx} className="VariableFilter__valueListItem">
                  <label>
                    <Input
                      type="checkbox"
                      checked={values.includes(value)}
                      value={value}
                      onChange={this.toggleValue}
                    />
                    {value}
                  </label>
                </li>
              );
            })}
            {!this.state.valuesAreComplete && (
              <li>
                <Button onClick={this.loadMore} disabled={this.state.loading}>
                  {this.state.loading ? 'loading...' : 'Load More'}
                </Button>
              </li>
            )}
          </ul>
        );
      case 'Boolean':
        return null;
      default:
        return (
          <ul className="VariableFilter__valueList VariableFilter__valueList--inputs">
            {values.map((value, idx) => {
              return (
                <li key={idx} className="VariableFilter__valueListItem">
                  <Input
                    type="text"
                    value={value}
                    data-idx={idx}
                    onChange={this.changeValue}
                    placeholder="Enter value"
                    isInvalid={!this.isValidInput(value)}
                  />
                  {values.length > 1 && (
                    <Button
                      onClick={evt => {
                        evt.preventDefault();
                        this.removeValue(idx);
                      }}
                      className="VariableFilter__removeItemButton"
                    >
                      ×
                    </Button>
                  )}
                </li>
              );
            })}
            {!this.selectionIsValid() && (
              <li className="VariableFilter__valueListWarning">
                <ErrorMessage text="All fields should have a numeric value" />
              </li>
            )}
            {
              <li className="VariableFilter__valueListButton">
                <Button onClick={this.addValue} className="VariableFilter__addValueButton">
                  Add Value
                </Button>
              </li>
            }
          </ul>
        );
    }
  };

  isValidInput = value => {
    return value.trim() && +value >= 0;
  };

  removeValue = index => {
    this.setState(prevState => {
      return {
        values: prevState.values.filter((_, idx) => idx !== index)
      };
    });
  };

  addValue = evt => {
    evt.preventDefault();

    this.setState(prevState => {
      return {
        values: [...prevState.values, '']
      };
    });
  };
}
