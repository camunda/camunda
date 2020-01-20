/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';
import DateRange from './DateRange';
import DateInput from './DateInput';
import moment from 'moment';
import {isDateValid} from './service';
import {Icon} from 'components';

import './DateFields.scss';

export default class DateFields extends React.PureComponent {
  constructor(props) {
    super(props);

    this.state = {
      popupOpen: false,
      currentlySelectedField: null,
      minDate: null,
      maxDate: null
    };
  }

  componentDidUpdate() {
    const {popupOpen} = this.state;

    if (popupOpen) {
      document.addEventListener('click', this.hidePopup);
      document.addEventListener('keydown', this.closeOnEscape);
    } else {
      document.removeEventListener('click', this.hidePopup);
      document.removeEventListener('keydown', this.closeOnEscape);
    }
  }

  componentWillUnmount() {
    document.removeEventListener('click', this.hidePopup);
    document.removeEventListener('keydown', this.closeOnEscape);
  }

  handleKeyPress = evt => {
    if (this.state.popupOpen && evt.key === 'Escape') {
      evt.stopPropagation();
    }
  };

  render() {
    const {startDate, endDate, format} = this.props;

    const startDateObj = moment(startDate, format);
    const endDateObj = moment(endDate, format);

    const startDateValid = isDateValid(startDate);
    const endDateValid = isDateValid(endDate);

    return (
      <div className="DateFields" onKeyDown={this.handleKeyPress}>
        <div className="DateFields__inputContainer">
          <DateInput
            className={classnames('DateInput__start', {
              'DateInput__start--highlight': this.isFieldSelected('startDate')
            })}
            format={format}
            onDateChange={this.setDate('startDate')}
            onFocus={() => {
              this.setState({currentlySelectedField: 'startDate'});
            }}
            onSubmit={this.submitStart}
            onClick={this.toggleDateRangeForStart}
            date={startDate}
            error={!startDateValid}
            disabled={this.props.disabled}
            icon={<Icon type="arrow-right" />}
          />
          <DateInput
            className={classnames('DateInput__end', {
              'DateInput__start--highlight': this.isFieldSelected('endDate')
            })}
            ref={this.saveEndDateField}
            format={format}
            onDateChange={this.setDate('endDate')}
            onFocus={() => {
              this.setState({currentlySelectedField: 'endDate'});
            }}
            onSubmit={this.submitEnd}
            onClick={this.toggleDateRangeForEnd}
            date={endDate}
            error={!endDateValid}
            disabled={this.props.disabled}
            icon={<Icon type="calender" />}
          />
        </div>
        {this.state.popupOpen && (
          <div
            onClick={this.stopClosingPopup}
            className={classnames('DateFields__range', {
              'DateFields__range--left': this.isFieldSelected('startDate'),
              'DateFields__range--right': this.isFieldSelected('endDate')
            })}
          >
            <DateRange
              format={format}
              onDateChange={this.onDateRangeChange}
              startDate={startDateObj}
              endDate={endDateObj}
              // reinitialize the component when one of the date inputs changes
              key={startDate + endDate}
            />
          </div>
        )}
      </div>
    );
  }

  submitStart = () => {
    this.setState({currentlySelectedField: 'endDate'});
    document.querySelector('.DateInput__end').focus();
  };

  submitEnd = () => {
    this.hidePopup();
    document.querySelector('.DateInput__end').blur();
  };

  closeOnEscape = event => {
    if (event.key === 'Escape') {
      this.hidePopup();
    }
  };

  saveEndDateField = input => (this.endDateField = input);

  onDateRangeChange = date => {
    this.props.onDateChange(this.state.currentlySelectedField, date.format('YYYY-MM-DD'));

    if (this.isFieldSelected('startDate')) {
      this.setState({
        currentlySelectedField: 'endDate'
      });
      this.endDateField.focus();
    } else {
      setTimeout(this.hidePopup, 350);
    }
  };

  stopClosingPopup = ({nativeEvent: event}) => {
    // https://stackoverflow.com/questions/24415631/reactjs-syntheticevent-stoppropagation-only-works-with-react-events
    event.stopImmediatePropagation();
  };

  hidePopup = () => {
    this.setState({
      popupOpen: false,
      currentlySelectedField: null,
      minDate: null,
      maxDate: null
    });
  };

  isFieldSelected(field) {
    return this.state.currentlySelectedField === field;
  }

  setDate = name => date => this.props.onDateChange(name, date);

  toggleDateRangeForStart = event => this.toggleDateRangePopup(event, 'startDate');
  toggleDateRangeForEnd = event => this.toggleDateRangePopup(event, 'endDate');

  toggleDateRangePopup = (event, field) => {
    this.stopClosingPopup(event);

    if (this.state.popupOpen) {
      return this.setState({
        currentlySelectedField: field
      });
    }

    this.setState({
      popupOpen: true,
      currentlySelectedField: field
    });
  };
}
