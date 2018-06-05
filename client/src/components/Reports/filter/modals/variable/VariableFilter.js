import React from 'react';
import classnames from 'classnames';
import debounce from 'debounce';
import {Modal, Button, Input, ControlGroup, ButtonGroup, ErrorMessage, Typeahead} from 'components';

import {loadVariables, loadValues} from './service';
import {numberParser} from 'services';

import './VariableFilter.css';

const valuesToLoad = 10;

export default class VariableFilter extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      selectedVariable: null,
      operator: 'in',
      values: [],
      availableValues: [],
      valuesAreComplete: false,
      loading: false,
      valuePrefix: ''
    };
  }

  componentDidMount = async () => {
    if (this.props.filterData) {
      const filterData = this.props.filterData[0].data;
      this.setState({
        selectedVariable: {name: filterData.name, type: filterData.type},
        operator: filterData.operator,
        values: filterData.values
      });
      this.loadAvailableValues({name: filterData.name, type: filterData.type});
    }
  };

  componentDidUpdate = (_, prevState) => {
    if (prevState.valuePrefix !== this.state.valuePrefix) {
      this.loadAvailableValues(this.state.selectedVariable);
    }
  };

  loadAvailableValues = debounce(async ({name, type}, more) => {
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
          valuesToLoad + 1 + (more ? this.state.availableValues.length : 0),
          this.state.valuePrefix
        );

        const valuesAreComplete =
          values.length !== valuesToLoad + 1 + (more ? this.state.availableValues.length : 0);

        this.setState({
          availableValues: valuesAreComplete ? values : values.splice(0, values.length - 1),
          valuesAreComplete,
          loading: false
        });
      }
    );
  }, 300);

  loadMore = evt => {
    evt.preventDefault();
    this.loadAvailableValues(this.state.selectedVariable, true);
  };

  selectVariable = async variable => {
    let values = [''];
    if (variable.type === 'Boolean') {
      values = [true];
    }
    if (variable.type === 'String') {
      values = [];
    }

    await this.loadAvailableValues(variable);

    this.setState({
      selectedVariable: variable,
      operator: variable.type === 'Boolean' ? '=' : 'in',
      values,
      valuesAreComplete: false
    });
  };

  render() {
    const {selectedVariable, operator, values, availableValues} = this.state;
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
            <div className="VariableFilter__buttonRow">
              <ButtonGroup className="VariableFilter__operatorButtons">
                {this.variableIsSelected() &&
                  this.renderOperatorButtons(selectedVariable.type, operator)}
              </ButtonGroup>
            </div>
            <div className="VariableFilter__valueFields">
              {this.variableIsSelected() &&
                this.renderValueFields(selectedVariable.type, availableValues, values)}
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

  getVariableName = variable => {
    if (variable) {
      return variable.name;
    }
    return null;
  };

  selectionIsValid = () => {
    let isValid = true;
    const variable = this.state.selectedVariable;

    if (variable && this.typeIsNumeric(variable.type)) {
      const containsOnlyValidNumbers = this.state.values.every(value => this.isValidInput(value));
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
    return this.state.selectedVariable !== null;
  };

  createFilter = evt => {
    evt.preventDefault();

    const variable = this.state.selectedVariable;

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

    const operator = evt.target.getAttribute('operator');
    const value = evt.target.getAttribute('value');

    const changes = {
      operator
    };

    if (value !== null) {
      // cast string value from DOM element to boolean
      changes.values = [value === 'true'];
    }

    if (operator === '<' || operator === '>') {
      changes.values = [this.state.values[0]];
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

  setValuePrefix = evt => {
    this.setState({
      valuePrefix: evt.target.value
    });
  };

  mapSelectedValues = values => {
    return (
      values.length > 0 && (
        <div>
          {values.map((value, idx) => {
            return (
              <li key={idx} className="VariableFilter__valueListItem">
                <label>
                  <Input type="checkbox" checked value={value} onChange={this.toggleValue} />
                  {value}
                </label>
              </li>
            );
          })}
          <hr />
        </div>
      )
    );
  };

  mapAvaliableValues = (availableValues, selectedValues) => {
    return availableValues.map((value, idx) => {
      if (!selectedValues.includes(value))
        return (
          <li key={idx} className="VariableFilter__valueListItem">
            <label>
              <Input
                type="checkbox"
                checked={selectedValues.includes(value)}
                value={value}
                onChange={this.toggleValue}
              />
              {value}
            </label>
          </li>
        );
      return null;
    });
  };

  renderValueFields = (type, availableValues, values) => {
    switch (type) {
      case 'String':
        const input = (
          <Input className="VariableFilter__string-value-input" onChange={this.setValuePrefix} />
        );

        if (availableValues.length === 0) {
          return (
            <div className="VariableFilter__string-value-selection">
              {input}
              <ul className="VariableFilter__valueList">
                {this.mapSelectedValues(values)}
                <li>No values match the query</li>
              </ul>
            </div>
          );
        }
        return (
          <div className="VariableFilter__string-value-selection">
            {input}
            <ul className="VariableFilter__valueList">
              {this.mapSelectedValues(values)}
              {this.mapAvaliableValues(availableValues, values)}
              {!this.state.valuesAreComplete && (
                <li>
                  <Button
                    className="VariableFilter__load-more-button"
                    onClick={this.loadMore}
                    disabled={this.state.loading}
                  >
                    {this.state.loading ? 'loading...' : 'Load More'}
                  </Button>
                </li>
              )}
            </ul>
          </div>
        );
      case 'Boolean':
        return null;
      default:
        const onlyOneValueAllowed = this.state.operator === '<' || this.state.operator === '>';
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
                <ErrorMessage>All fields should have a numeric value</ErrorMessage>
              </li>
            )}
            {!onlyOneValueAllowed && (
              <li className="VariableFilter__valueListButton">
                <Button onClick={this.addValue} className="VariableFilter__addValueButton">
                  Add Value
                </Button>
              </li>
            )}
          </ul>
        );
    }
  };

  isValidInput = value => {
    const type = this.state.selectedVariable.type;

    return this.typeIsFloating(type)
      ? numberParser.isFloatNumber(value)
      : numberParser.isIntegerNumber(value);
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
