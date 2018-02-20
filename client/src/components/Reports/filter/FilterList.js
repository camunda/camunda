import React from 'react';
import moment from 'moment';

import {ActionItem} from 'components';
import {getFlowNodeNames} from 'services';

import './FilterList.css';

export default class FilterList extends React.Component {

  constructor(props){
    super(props);
    this.state = {
      flowNodeNames: null
    }
    this.loadFlowNodeNames();
  }

  componentDidUpdate(prevProps, prevState) {
    if (prevProps.processDefinitionId !== this.props.processDefinitionId) {
      this.loadFlowNodeNames();
    }
  }

  loadFlowNodeNames = async () => {
    if(this.props.processDefinitionId) {
      const flowNodeNames = await getFlowNodeNames(this.props.processDefinitionId);
      this.setState({flowNodeNames})
    }
  }

  createOperator  = (name) => {
    return <span className='FilterList__operator'> {name} </span>;
  }

  render() {
    const list = [];

    for(let i = 0; i < this.props.data.length; i++) {
      const filter = this.props.data[i];

      if(filter.type === 'date') {
        // combine two separate filter entries into one date filter pill
        const nextFilter = this.props.data[i + 1];

        list.push(<li key={i} onClick={this.props.openEditFilterModal(filter, nextFilter)} className='FilterList__item'>
          <ActionItem onClick={(evt) => {evt.stopPropagation(); this.props.deleteFilter(filter, nextFilter)}}>
            <span className='FilterList__parameter-name'>Start Date </span> is between
            {' '}<span className='FilterList__value'>{moment(filter.data.value).format('YYYY-MM-DD')}</span>{' '}
            {this.createOperator('and')}
            {' '}<span className='FilterList__value'>{moment(nextFilter.data.value).format('YYYY-MM-DD')}</span>
          </ActionItem>
        </li>);

        i++;
      } else {
        if(filter.type === 'variable') {
          const {name, operator, values} = filter.data;

          list.push(<li key={i} onClick={this.props.openEditFilterModal(filter)} className='FilterList__item'>
          <ActionItem onClick={(evt) => {evt.stopPropagation(); this.props.deleteFilter(filter)}}>
              <span className='FilterList__parameter-name'>{name}</span>
              {(operator === 'in' || operator === '=') && this.createOperator('is')}
              {operator === 'not in' && (
                values.length === 1 ? this.createOperator('is not') : this.createOperator('is neither')
              )}
              {operator === '<' && this.createOperator('is less than')}
              {operator === '>' && this.createOperator('is greater than')}
              {values.map((value, idx) => {
                return <span key={idx}>
                  <span className='FilterList__value'>{value.toString()}</span>
                  {idx < values.length - 1 && (
                    operator === 'not in' ? this.createOperator('nor') : this.createOperator('or')
                  )}
                </span>
              })}
            </ActionItem>
          </li>);
        } else if(filter.type === 'executedFlowNodes') {
          const {values} = filter.data;
          const flowNodes = this.state.flowNodeNames;

          list.push(<li key={i} onClick={this.props.openEditFilterModal(filter)} className='FilterList__item'>
          <ActionItem onClick={(evt) => {evt.stopPropagation(); this.props.deleteFilter(filter)}}>
              <span className='FilterList__parameter-name'>Executed Flow Node</span> is{' '}
              {values.map((value, idx) => {
                return <span key={idx}>
                  <span className='FilterList__value'>{(flowNodes) ? flowNodes[value.toString()] : value.toString()}</span>
                  {idx < values.length - 1 && this.createOperator('or')}
                </span>
              })}
            </ActionItem>
          </li>);
        } else if(filter.type === 'rollingDate') {
          const {unit, value} = filter.data;

          list.push(<li key={i} onClick={this.props.openEditFilterModal(filter)} className='FilterList__item'>
          <ActionItem onClick={(evt) => {evt.stopPropagation(); this.props.deleteFilter(filter)}}>
              <span className='FilterList__parameter-name'>Start Date </span>
              less than
              {' '}<span className='FilterList__value'>{value.toString()} {unit.slice(0,-1)}{value > 1 && 's'}</span>{' '}
              ago
            </ActionItem>
          </li>);
        } else if(filter.type === 'processInstanceDuration') {
          const {unit, value, operator} = filter.data;

          list.push(<li key={i} onClick={this.props.openEditFilterModal(filter)} className='FilterList__item'>
          <ActionItem onClick={(evt) => {evt.stopPropagation(); this.props.deleteFilter(filter)}}>
              <span className='FilterList__parameter-name'>Duration</span>
              {operator === '<' && this.createOperator('is less than')}
              {operator === '>' && this.createOperator('is more than')}
              <span className='FilterList__value'>{value.toString()} {unit.slice(0,-1)}{value > 1 && 's'}</span>
            </ActionItem>
          </li>);
        } else if (filter.type === 'runningInstancesOnly') {
          list.push(<li key={i} className='FilterList__item'>
            <ActionItem onClick={(evt) => {evt.stopPropagation(); this.props.deleteFilter(filter)}}>
              <span className='FilterList__parameter-name'>Running Process Instances Only</span>
            </ActionItem>
          </li>);
        }
      }

      if(i < this.props.data.length - 1) {
        list.push(<li className='FilterList__itemConnector' key={'connector_' + i}>and</li>);
      }
    }

    return <ul className='FilterList'>
      {list}
    </ul>
  }
}
