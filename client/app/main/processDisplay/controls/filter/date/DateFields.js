import React from 'react';
import {DateRange} from './DateRange';
import {DateInput} from './DateInput';
import classwrap from 'classwrap';

const jsx = React.createElement;
const $document = document; // for mocking
const $setTimeout = setTimeout; // for mocking

export class DateFields extends React.PureComponent {
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
      $document.addEventListener('click', this.hidePopup);
    } else {
      $document.removeEventListener('click', this.hidePopup);
    }
  }

  componentWillUnmount() {
    $document.removeEventListener('click', this.hidePopup);
  }

  render() {
    return <center className="date-fields">
      <div className="input-group input-daterange">
        <DateInput
            className={
              classwrap([
                'form-control',
                'start',
                {
                  'date-field-highlight': this.isFieldSelected('startDate')
                }
              ])
            }
            format={this.props.format}
            onDateChange={this.setStartDate}
            onClick={this.toggleDateRangeForStart}
            date={this.props.startDate} />
        <span className="input-group-addon">to</span>
        <DateInput
            className={
              classwrap([
                'form-control',
                'end',
                {
                  'date-field-highlight': this.isFieldSelected('endDate')
                }
              ])
            }
            reference={this.saveEndDateField}
            format={this.props.format}
            onDateChange={this.setEndDate}
            onClick={this.toggleDateRangeForEnd}
            date={this.props.endDate} />
      </div>
      <div onClick={this.stopClosingPopup} className={
        classwrap([
          'date-fields-range',
          {
            hidden: !this.state.popupOpen,
            'left-side': this.isFieldSelected('startDate'),
            'right-side': this.isFieldSelected('endDate')
          }
        ])
      }>
        <DateRange
            format={this.props.format}
            onDateChange={this.onDateRangeChange}
            startDate={this.props.startDate}
            endDate={this.props.endDate} />
      </div>
    </center>;
  }

  saveEndDateField = input => this.endDateField = input;

  onDateRangeChange = date => {
    this.props.onDateChange(this.state.currentlySelectedField, date);

    if (this.isFieldSelected('startDate')) {
      this.setState({
        currentlySelectedField: 'endDate',
      });
    } else {
      $setTimeout(this.hidePopup, 350);
    }
  }

  stopClosingPopup = ({nativeEvent: event}) =>  {
    // https://stackoverflow.com/questions/24415631/reactjs-syntheticevent-stoppropagation-only-works-with-react-events
    event.stopImmediatePropagation();
  }

  hidePopup = () => {
    this.setState({
      popupOpen: false,
      currentlySelectedField: null,
      minDate: null,
      maxDate: null
    });
  }

  isFieldSelected(field) {
    return this.state.currentlySelectedField === field;
  }

  setStartDate = date => this.props.onDateChange('startDate', date);
  setEndDate = date => this.props.onDateChange('endDate', date);

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
  }
}
