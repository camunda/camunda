import React from 'react';
import {Calendar} from 'react-date-range';
import {sortDates} from './service';

const jsx = React.createElement;

const theme = {
  DayInRange: {
    color: 'black',
    background: '#eeeeee'
  },
  DayActive: {
    background: '#871020'
  },
  DaySelected: {
    background: '#871020'
  }
};

export class DateRange extends React.Component {
  constructor(props) {
    super(props);

    this.state = this.sortRange({
      startLink: this.props.startDate.clone(),
      endLink: this.props.endDate.clone()
    });
  }

  render() {
    const range = {
      startDate: this.props.startDate,
      endDate: this.props.endDate
    };

    return <div>
      <Calendar format="YYYY-MM-DD"
              link={this.state.startLink}
              linkCB={this.changeMonth('startLink')}
              range={range}
              theme={theme}
              firstDayOfWeek={1}
              onChange={this.getDateSetter('startDate')} />

      <Calendar format="YYYY-MM-DD"
              link={this.state.endLink}
              linkCB={this.changeMonth('endLink')}
              theme={theme}
              range={range}
              firstDayOfWeek={1}
              onChange={this.getDateSetter('endDate')} />
    </div>;
  }

  getDateSetter = name => date => this.props.onDateChange(name, date)

  componentWillReceiveProps(newProps) {
    this.setState(
      this.sortRange({
        startLink: newProps.startDate.clone(),
        endLink: newProps.endDate.clone()
      })
    );
  }

  sortRange({startLink, endLink, startDate, endDate}) {
    const {start: newStartLink, end: newEndLink} = sortDates({
      start: startLink,
      end: endLink
    });

    return {
      startLink: newStartLink,
      endLink: newEndLink
    };
  }

  changeMonth = name => {
    return direction => {
      const newLink = this.state[name]
        .clone()
        .add(direction, 'months');
      const beforeStart = name === 'endLink' && newLink.isBefore(this.state.startLink);
      const afterEnd = name === 'startLink' && newLink.isAfter(this.state.endLink);

      if (beforeStart || afterEnd) {
        return this.setState({
          ...this.state,
          'startLink': newLink,
          'endLink': newLink
        });
      }

      this.setState({
        ...this.state,
        [name]: newLink
      });
    };
  }
}
