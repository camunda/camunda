import React from 'react';
import moment from 'moment';

import {Button} from 'components';

import './FilterList.css';

export default class FilterList extends React.Component {
  render() {
    const list = [];

    for(let i = 0; i < this.props.data.length; i++) {
      const filter = this.props.data[i];

      if(filter.type === 'date') {
        // combine two separate filter entries into one date filter pill
        const nextFilter = this.props.data[i + 1];

        list.push(<li key={i} className='FilterList__item'>
          <Button onClick={() => this.props.deleteFilter(filter, nextFilter)} className='FilterList__deleteButton'>×</Button>
          <span className='FilterList__item-content'>
            Start Date between
            {' '}<span className='FilterList__value'>{moment(filter.data.value).format('YYYY-MM-DD')}</span>{' '}
            and
            {' '}<span className='FilterList__value'>{moment(nextFilter.data.value).format('YYYY-MM-DD')}</span>{' '}
          </span>
        </li>);

        i++;
      } else {
        if(filter.type === 'variable') {
          const {name, operator, values} = filter.data;

          list.push(<li key={i} className='FilterList__item'>
            <Button onClick={() => this.props.deleteFilter(filter)}  className='FilterList__deleteButton'>×</Button>
            <span className='FilterList__item-content'>
              <span className='FilterList__variable-name'>{name}</span>
                {(operator === 'in' || operator === '=') && ' is '}
                {operator === 'not in' && (
                  values.length === 1 ? ' is not ' : ' is neither '
                )}
                {operator === '<' && ' is less than '}
                {operator === '>' && ' is greater than '}
              {values.map((value, idx) => {
                return <span key={idx}>
                  <span className='FilterList__value'>{value.toString()}</span>
                  {idx < values.length - 1 && (
                    operator === 'not in' ? ' nor ' : ' or '
                  )}
                </span>
              })}
            </span>
          </li>);
        }

        if(filter.type === 'executedFlowNodes') {
          const {values} = filter.data;

          list.push(<li key={i} className='FilterList__item'>
            <Button onClick={() => this.props.deleteFilter(filter)}  className='FilterList__deleteButton'>×</Button>
            <span className='FilterList__item-content'>
              executed flow node {values.map((value, idx) => {
                return <span key={idx}>
                  <span className='FilterList__value'>{value.toString()}</span>
                  {idx < values.length - 1 && ' or '}
                </span>
              })}
            </span>
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
