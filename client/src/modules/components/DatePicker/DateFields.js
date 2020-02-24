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

import './DateFields.scss';

export default class DateFields extends React.PureComponent {
  state = {
    popupOpen: false,
    currentlySelectedField: null
  };

  endDateField = React.createRef();

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

    const startDateObj = moment(startDate, format, true);
    const endDateObj = moment(endDate, format, true);

    return (
      <div className="DateFields" onKeyDown={this.handleKeyPress}>
        <div className="inputContainer">
          <DateInput
            className={classnames({
              highlight: this.isFieldSelected('startDate')
            })}
            onChange={this.setDate('startDate')}
            onFocus={() => {
              this.setState({currentlySelectedField: 'startDate'});
            }}
            onSubmit={this.submitStart}
            onClick={this.toggleDateRangeForStart}
            value={startDate}
            isInvalid={!isDateValid(startDate)}
            disabled={this.props.disabled}
          />
          <DateInput
            className={classnames({
              highlight: this.isFieldSelected('endDate')
            })}
            ref={this.endDateField}
            onChange={this.setDate('endDate')}
            onFocus={() => {
              this.setState({currentlySelectedField: 'endDate'});
            }}
            onSubmit={this.submitEnd}
            onClick={this.toggleDateRangeForEnd}
            value={endDate}
            isInvalid={!isDateValid(endDate)}
            disabled={this.props.disabled}
          />
        </div>
        {this.state.popupOpen && (
          <div
            onClick={this.stopClosingPopup}
            className={classnames('dateRangeContainer', {
              dateRangeContainerLeft: this.isFieldSelected('startDate'),
              dateRangeContainerRight: this.isFieldSelected('endDate')
            })}
          >
            <DateRange
              endDateSelected={this.isFieldSelected('endDate')}
              onDateChange={this.onDateRangeChange}
              startDate={startDateObj}
              endDate={endDateObj}
            />
          </div>
        )}
      </div>
    );
  }

  submitStart = () => {
    this.setState({currentlySelectedField: 'endDate'});
    this.endDateField.current.focus();
  };

  submitEnd = () => {
    this.hidePopup();
    this.endDateField.current.blur();
  };

  closeOnEscape = event => {
    if (event.key === 'Escape') {
      this.hidePopup();
    }
  };

  formatDate = date => moment(date).format(this.props.format);

  onDateRangeChange = ({startDate, endDate}) => {
    this.props.onDateChange('startDate', this.formatDate(startDate));
    this.props.onDateChange('endDate', this.formatDate(endDate));

    if (this.isFieldSelected('startDate')) {
      this.setState({currentlySelectedField: 'endDate'});
      this.endDateField.current.focus();
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
      currentlySelectedField: null
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
