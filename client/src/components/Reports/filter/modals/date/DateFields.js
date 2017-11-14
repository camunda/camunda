import React from 'react';
import DateRange from './DateRange';
import DateInput from './DateInput';

import './DateFields.css';

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

  render() {
    return <center className="DateFields">
      <div className='DateFields__inputContainer'>
        <DateInput
            className={'DateInput__start' + (this.isFieldSelected('startDate') ? ' DateInput__start--highlight' : '')}
            format={this.props.format}
            onDateChange={this.setStartDate}
            onClick={this.toggleDateRangeForStart}
            date={this.props.startDate} />
        <span>to</span>
        <DateInput
            className={'DateInput__end' + (this.isFieldSelected('endDate') ? ' DateInput__start--highlight' : '')}
            reference={this.saveEndDateField}
            format={this.props.format}
            onDateChange={this.setEndDate}
            onClick={this.toggleDateRangeForEnd}
            date={this.props.endDate} />
      </div>
      {
        this.state.popupOpen && (
          <div onClick={this.stopClosingPopup} className={
            'DateFields__range' +
            (this.isFieldSelected('startDate') ? ' DateFields__range--left' : '') +
            (this.isFieldSelected('endDate') ? ' DateFields__range--right' : '')
          }>
            <DateRange
                format={this.props.format}
                onDateChange={this.onDateRangeChange}
                startDate={this.props.startDate}
                endDate={this.props.endDate} />
          </div>
        )
      }
    </center>;
  }

  closeOnEscape = event => {
    if (event.key === 'Escape') {
      this.hidePopup();
    }
  }

  saveEndDateField = input => this.endDateField = input;

  onDateRangeChange = date => {
    this.props.onDateChange(this.state.currentlySelectedField, date);

    if (this.isFieldSelected('startDate')) {
      this.setState({
        currentlySelectedField: 'endDate',
      });
      this.endDateField.focus();
    } else {
      setTimeout(this.hidePopup, 350);
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
