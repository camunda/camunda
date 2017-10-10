import React from 'react';
import {Calendar} from 'react-date-range';

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
  },
  DayPassive: {
    cursor: 'default'
  }
};

export class DateRange extends React.Component {
  constructor(props) {
    super(props);

    this.state = this.adjustRange({
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
      <Calendar format="this.props.format"
              link={this.state.startLink}
              linkCB={this.changeStartMonth}
              range={range}
              theme={theme}
              minDate={this.props.minDate}
              maxDate={this.props.maxDate}
              firstDayOfWeek={1}
              onChange={this.props.onDateChange}
              classNames={this.getClassesForCalendar(true)} />

      <Calendar format="this.props.format"
              link={this.state.endLink}
              linkCB={this.changeEndMonth}
              theme={theme}
              range={range}
              minDate={this.props.minDate}
              maxDate={this.props.maxDate}
              firstDayOfWeek={1}
              onChange={this.props.onDateChange}
              classNames={this.getClassesForCalendar(false)} />
    </div>;
  }

  getClassesForCalendar(first) {
    if (first && this.state.innerArrowsDisabled) {
      return {
        nextButton: 'hidden'
      };
    }

    if (!first && this.state.innerArrowsDisabled) {
      return {
        prevButton: 'hidden'
      };
    }
  }

  componentWillReceiveProps(newProps) {
    this.setState(
      this.adjustRange({
        startLink: newProps.startDate.clone(),
        endLink: newProps.endDate.clone()
      })
    );
  }

  adjustRange({startLink, endLink}) {
    if (startLink.startOf('month').isSame(endLink.startOf('month'))) {
      endLink.add(1, 'months');
    }

    return {
      startLink: startLink,
      endLink: endLink,
      innerArrowsDisabled: this.shouldDisableInnerArrows(startLink, endLink)
    };
  }

  changeStartMonth = direction => this.changeMonth('startLink', direction);

  changeEndMonth = direction => this.changeMonth('endLink', direction);

  changeMonth = (name, direction) => {
    const newLink = this.state[name]
      .clone()
      .add(direction, 'months');

    this.setState(
      this.adjustRange({
        ...this.state,
        [name]: newLink
      })
    );
  }

  shouldDisableInnerArrows(startLink, endLink) {
    return startLink
      .clone()
      .add(1, 'months')
      .startOf('month')
      .isSame(
        endLink.startOf('month')
      );
  }
}
