import React from 'react';
import {Modal, Button, Select, Input} from 'components';

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

    this.loadAvailableVariables();
  }

  loadAvailableVariables = async () => {
    this.setState({
      variables: await loadVariables(this.props.processDefinitionId)
    });
  }

  loadAvailableValues = async ({name, type}) => {
    this.setState({
      loading: true
    }, async () => {
      const values = await loadValues(this.props.processDefinitionId, name, type, 0, this.state.availableValues.length + valuesToLoad);

      this.setState({
        availableValues: values,
        valuesAreComplete: values.length !== (this.state.availableValues.length + valuesToLoad),
        loading: false
      });
    });

  }

  loadMore = evt => {
    evt.preventDefault();
    this.loadAvailableValues(this.state.variables[this.state.selectedVariableIdx]);
  }

  selectVariable = ({target: {value}}) => {
    const variable = this.state.variables[value];

    let values = [''];
    if(variable.type === 'Boolean') {
      values = [true];
    }
    if(variable.type === 'String') {
      values = [];
    }

    this.setState({
      selectedVariableIdx: value,
      operator: variable.type === 'Boolean' ? '=' : 'in',
      values,
      availableValues: [],
      valuesAreComplete: false
    }, () => {
      if(variable.type === 'String') {
        this.loadAvailableValues(variable);
      }
    });
  }

  render() {
    const {variables, selectedVariableIdx, operator, values, availableValues} = this.state;

    return (<Modal open={true} onClose={this.props.close} className='VariableFilter__modal'>
      <Modal.Header>New Variable Filter</Modal.Header>
      <Modal.Content>
        <form>
          <span>Variable Name</span>
          <Select value={selectedVariableIdx} onChange={this.selectVariable}>
            <Select.Option disabled value={-1}>Please Select Variable</Select.Option>
            {variables.map(({name}, idx) => {
              return <Select.Option value={idx} key={idx}>{name}</Select.Option>
            })}
          </Select>
          <div className='VariableFilter__operatorButtons'>
            {this.variableIsSelected() && this.renderOperatorButtons(
              variables[selectedVariableIdx].type,
              operator
            )}
          </div>
          <div className='VariableFilter__valueFields'>
            {this.variableIsSelected() && this.renderValueFields(
              variables[selectedVariableIdx].type,
              availableValues,
              values
            )}
          </div>
        </form>
      </Modal.Content>
      <Modal.Actions>
        <Button onClick={this.props.close}>Abort</Button>
        <Button type='primary' className='Button--blue' onClick={this.createFilter}>Create Filter</Button>
      </Modal.Actions>
    </Modal>
    );
  }

  variableIsSelected = () => {
    return this.state.selectedVariableIdx !== -1;
  }

  createFilter = evt => {
    evt.preventDefault();

    const variable = this.state.variables[this.state.selectedVariableIdx];

    let values;
    if(variable.type === 'String' || variable.type === 'Boolean') {
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
  }

  selectOperator = evt => {
    evt.preventDefault();

    const changes = {
      operator: evt.target.getAttribute('operator')
    };

    const value = evt.target.getAttribute('value');
    if(value !== null) {
      // cast string value from DOM element to boolean
      changes.values = [value === 'true'];
    }

    this.setState(changes);
  }

  toggleValue = ({target: {checked, value}}) => {
    this.setState(prevState => {
      if(checked) {
        return {
          values: prevState.values.concat(value)
        };
      } else {
        return {
          values: prevState.values.filter(existingValue => existingValue !== value)
        };
      }
    });
  }

  changeValue = ({target}) => {
    this.setState(prevState => {
      const newValues = [...prevState.values];
      newValues[target.getAttribute('data-idx')] = target.value;

      return {
        values: newValues
      };
    });
  }

  renderOperatorButtons = (type, selection) => {
    switch(type) {
      case 'String': return [
        <Button key='=' onClick={this.selectOperator} operator='in' className={this.state.operator === 'in' ? 'VariableFilter__operatorButton--active' : ''}>is</Button>,
        <Button key='!=' onClick={this.selectOperator} operator='not in' className={this.state.operator === 'not in' ? 'VariableFilter__operatorButton--active' : ''}>is not</Button>
      ];
      case 'Boolean': return [
        <Button key='true' onClick={this.selectOperator} operator='=' className={this.state.values[0] === true ? 'VariableFilter__operatorButton--active' : ''} value={true}>is true</Button>,
        <Button key='false' onClick={this.selectOperator} operator='=' className={this.state.values[0] === false ? 'VariableFilter__operatorButton--active' : ''} value={false}>is false</Button>
      ];
      default: return [
        <Button key='=' onClick={this.selectOperator} className={this.state.operator === 'in' ? 'VariableFilter__operatorButton--active' : ''} operator='in'>is</Button>,
        <Button key='!=' onClick={this.selectOperator} className={this.state.operator === 'not in' ? 'VariableFilter__operatorButton--active' : ''} operator='not in'>is not</Button>,
        <Button key='<' onClick={this.selectOperator} className={this.state.operator === '<' ? 'VariableFilter__operatorButton--active' : ''} operator='<'>is less than</Button>,
        <Button key='>' onClick={this.selectOperator} className={this.state.operator === '>' ? 'VariableFilter__operatorButton--active' : ''} operator='>'>is greater than</Button>
      ];
    }
  }

  renderValueFields = (type, availableValues, values) => {
    switch(type) {
      case 'String':
        if(availableValues.length === 0) {
          return 'loading...';
        }
        return <ul>
          {availableValues.map((value, idx) => {
            return <li key={idx}>
              <label>
                <Input type='checkbox' checked={values.includes(value)} value={value} onChange={this.toggleValue} />
                {value}
              </label>
            </li>
          })}
          {!this.state.valuesAreComplete && <li><Button onClick={this.loadMore} disabled={this.state.loading}>
            {this.state.loading ? 'loading...' : 'Load More'}
          </Button></li>}
        </ul>
      case 'Boolean':
        return null;
      default:
        return <ul>
          {values.map((value, idx) => {
            return <li key={idx}>
              <Input type='text' value={value} data-idx={idx} onChange={this.changeValue} placeholder='Enter value' />
            </li>
          })}
          {values[values.length - 1] && <li><Button onClick={this.addValue}>Add Another Value</Button></li>}
        </ul>
    }
  }

  addValue = evt => {
    evt.preventDefault();

    this.setState(prevState => {
      return {
        values: [...prevState.values, '']
      };
    });
  }
}
