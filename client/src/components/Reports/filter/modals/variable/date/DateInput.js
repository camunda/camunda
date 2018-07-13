import React from 'react';
import moment from 'moment';

import {DatePicker} from 'components';

export default class DateInput extends React.Component {
  static defaultFilter = {startDate: moment(), endDate: moment()};

  componentDidMount() {
    this.props.setValid(true);
  }

  render() {
    return (
      <DatePicker
        onDateChange={this.onDateChange}
        initialDates={{
          startDate: this.props.filter.startDate,
          endDate: this.props.filter.endDate
        }}
      />
    );
  }

  onDateChange = ({startDate, endDate, valid}) => {
    this.props.changeFilter({startDate, endDate});
    this.props.setValid(valid);
  };

  static parseFilter = ([start, end]) => {
    return {
      startDate: moment(start.data.values[0]),
      endDate: moment(end.data.values[0])
    };
  };

  static addFilter = (addFilter, variable, filter) => {
    addFilter(
      {
        type: 'variable',
        data: {
          name: variable.name,
          type: variable.type,
          operator: '>=',
          values: [filter.startDate.startOf('day').format('YYYY-MM-DDTHH:mm:ss')]
        }
      },
      {
        type: 'variable',
        data: {
          name: variable.name,
          type: variable.type,
          operator: '<=',
          values: [filter.endDate.endOf('day').format('YYYY-MM-DDTHH:mm:ss')]
        }
      }
    );
  };
}
