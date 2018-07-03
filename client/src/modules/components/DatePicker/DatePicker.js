import React from 'react';
import moment from 'moment';

import {ButtonGroup} from 'components';

import './DatePicker.css';

import DateFields from './DateFields';
import DateButton from './DateButton';

export default class DatePicker extends React.Component {
  constructor(props) {
    super(props);

    const initialDates = this.props.initialDates || {};

    this.state = {
      startDate: initialDates.startDate || moment(),
      endDate: initialDates.endDate || moment(),
      valid: true
    };
  }

  render() {
    return (
      <React.Fragment>
        <div className="DatePicker__inputs">
          <DateFields
            format="YYYY-MM-DD"
            onDateChange={this.onDateChange}
            startDate={this.state.startDate}
            endDate={this.state.endDate}
            setValidState={this.setValidState}
          />
        </div>
        <div className="DatePicker__buttons">
          <ButtonGroup className="DatePicker__buttonRow">
            {this.getDateButtons([
              DateButton.TODAY,
              DateButton.YESTERDAY,
              DateButton.PAST7,
              DateButton.PAST30
            ])}
          </ButtonGroup>
          <ButtonGroup className="DatePicker__buttonRow">
            {this.getDateButtons([
              DateButton.THIS_WEEK,
              DateButton.THIS_MONTH,
              DateButton.THIS_YEAR
            ])}
          </ButtonGroup>
          <ButtonGroup className="DatePicker__buttonRow">
            {this.getDateButtons([
              DateButton.LAST_WEEK,
              DateButton.LAST_MONTH,
              DateButton.LAST_YEAR
            ])}
          </ButtonGroup>
        </div>
      </React.Fragment>
    );
  }

  componentDidUpdate(prevProps, prevState) {
    if (
      this.props.onDateChange &&
      (this.state.startDate !== prevState.startDate ||
        this.state.endDate !== prevState.endDate ||
        this.state.valid !== prevState.valid)
    ) {
      this.props.onDateChange(this.state);
    }
  }

  setValidState = valid => this.setState({valid});

  getDateButtons(labels) {
    return labels.map(label => (
      <DateButton dateLabel={label} key={label} setDates={this.setDates} />
    ));
  }

  setDates = dates => {
    this.setState({...dates, valid: true});
  };

  onDateChange = (name, date) => {
    if (
      (name === 'startDate' && date.isAfter(this.state.endDate)) ||
      (name === 'endDate' && date.isBefore(this.state.startDate))
    ) {
      return this.setState({
        startDate: date,
        endDate: date.clone()
      });
    }

    this.setState({
      [name]: date
    });
  };
}
