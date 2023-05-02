/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {KeyboardEventHandler, PureComponent, createRef} from 'react';
import classnames from 'classnames';
import {parseISO} from 'date-fns';

import {format} from 'dates';

import DateRange from './DateRange';
import PickerDateInput from './PickerDateInput';
import {isDateValid} from './service';

import './DateFields.scss';

const POPPUP_CLASSNAME = 'dateRangeContainer';

interface DateFieldsProps {
  startDate: string;
  endDate: string;
  forceOpen?: boolean;
  type: string;
  format: string;
  onDateChange: (type: DateFieldName, value: string) => void;
}

export type DateFieldName = 'startDate' | 'endDate';

interface DateFieldsState {
  popupOpen: boolean;
  currentlySelectedField: DateFieldName | null;
}

export default class DateFields extends PureComponent<DateFieldsProps, DateFieldsState> {
  state: DateFieldsState = {
    popupOpen: false,
    currentlySelectedField: null,
  };

  dateFields = createRef<HTMLDivElement>();

  endDateField = createRef<HTMLInputElement>();

  // Modals stop propagation of events on elements outside the modal
  // Therefore, we need to attach the events on the modal instead of document
  getContext = () => this.dateFields.current?.closest<HTMLElement>('.Modal') || document;

  componentDidUpdate() {
    const {popupOpen} = this.state;

    const context = this.getContext() as HTMLElement;

    if (popupOpen) {
      context.addEventListener('click', this.hidePopup);
      context.addEventListener('keydown', this.closeOnEscape);
    } else {
      context.removeEventListener('click', this.hidePopup);
      context.removeEventListener('keydown', this.closeOnEscape);
    }
  }

  componentWillUnmount() {
    const context = this.getContext() as HTMLElement;
    context.removeEventListener('click', this.hidePopup);
    context.removeEventListener('keydown', this.closeOnEscape);
  }

  handleKeyPress: KeyboardEventHandler = (evt) => {
    if (this.state.popupOpen && evt.key === 'Escape') {
      evt.stopPropagation();
    }
  };

  render() {
    const {startDate, endDate, forceOpen, type} = this.props;

    const startDateObj = parseISO(startDate);
    const endDateObj = parseISO(endDate);

    return (
      <div className="DateFields" ref={this.dateFields} onKeyDown={this.handleKeyPress}>
        <div className="inputContainer">
          {type !== 'before' && (
            <PickerDateInput
              className={classnames({
                highlight: this.isFieldSelected('startDate'),
              })}
              onChange={this.setDate('startDate')}
              onFocus={() => {
                this.setState({currentlySelectedField: 'startDate'});
              }}
              onSubmit={this.submitStart}
              onClick={() => this.toggleDateRangePopup('startDate')}
              value={startDate}
              isInvalid={!isDateValid(startDate)}
            />
          )}
          {type !== 'after' && (
            <PickerDateInput
              className={classnames({
                highlight: this.isFieldSelected('endDate'),
              })}
              ref={this.endDateField}
              onChange={this.setDate('endDate')}
              onFocus={() => {
                this.setState({currentlySelectedField: 'endDate'});
              }}
              onSubmit={this.submitEnd}
              onClick={() => this.toggleDateRangePopup('endDate')}
              value={endDate}
              isInvalid={!isDateValid(endDate)}
            />
          )}
        </div>
        {(this.state.popupOpen || forceOpen) && (
          <div
            className={classnames(POPPUP_CLASSNAME, {
              dateRangeContainerLeft: this.isFieldSelected('startDate'),
              dateRangeContainerRight: this.isFieldSelected('endDate'),
            })}
          >
            <DateRange
              type={type}
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
    if (this.props.type === 'between') {
      this.setState({currentlySelectedField: 'endDate'});
      this.endDateField.current?.focus();
    } else {
      this.hidePopup();
    }
  };

  submitEnd = () => {
    this.hidePopup();
    this.endDateField.current?.blur();
  };

  closeOnEscape = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      this.hidePopup();
    }
  };

  formatDate = (date?: Date | null) => (date ? format(date, this.props.format) : '');

  onDateRangeChange = ({
    startDate,
    endDate,
  }: {startDate?: Date | null; endDate?: Date | null} = {}) => {
    this.props.onDateChange('startDate', this.formatDate(startDate));
    this.props.onDateChange('endDate', this.formatDate(endDate));

    if (this.isFieldSelected('endDate') || this.props.type !== 'between') {
      setTimeout(this.hidePopup, 350);
    } else {
      this.setState({currentlySelectedField: 'endDate'});
      this.endDateField.current?.focus();
    }
  };

  hidePopup = (evt?: Event) => {
    if (!(evt?.target as HTMLElement)?.closest('.' + POPPUP_CLASSNAME)) {
      this.setState({
        popupOpen: false,
        currentlySelectedField: null,
      });
    }
  };

  isFieldSelected(field: DateFieldName): boolean {
    return this.state.currentlySelectedField === field;
  }

  setDate = (name: DateFieldName) => (date: string) => this.props.onDateChange(name, date);

  toggleDateRangePopup = (field: DateFieldName) => {
    this.setState({
      popupOpen: true,
      currentlySelectedField: field,
    });
  };
}
