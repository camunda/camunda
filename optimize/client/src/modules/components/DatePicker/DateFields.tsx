/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {KeyboardEventHandler, PureComponent, createRef} from 'react';
import classnames from 'classnames';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import {t} from 'translation';

import DateRange from './DateRange';
import PickerDateInput from './PickerDateInput';
import {isDateValid} from './service';

import './DateFields.scss';

const POPUP_CLASSNAME = 'dateRangeContainer';

interface DateFieldsProps {
  startDate: string;
  endDate: string;
  forceOpen?: boolean;
  type: 'between' | 'after' | 'before';
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
              id="dateFieldsBefore"
              labelText={t('common.filter.dateModal.unit.before')}
              hideLabel
              onChange={this.setDate('startDate')}
              onFocus={() => {
                this.setState({currentlySelectedField: 'startDate'});
              }}
              onSubmit={this.submitStart}
              onClick={() => this.toggleDateRangePopup('startDate')}
              value={startDate}
              invalid={!isDateValid(startDate)}
            />
          )}
          {type !== 'after' && (
            <PickerDateInput
              id="dateFieldsAfter"
              labelText={t('common.filter.dateModal.unit.after')}
              hideLabel
              ref={this.endDateField}
              onChange={this.setDate('endDate')}
              onFocus={() => {
                this.setState({currentlySelectedField: 'endDate'});
              }}
              onSubmit={this.submitEnd}
              onClick={() => this.toggleDateRangePopup('endDate')}
              value={endDate}
              invalid={!isDateValid(endDate)}
            />
          )}
        </div>
        {(this.state.popupOpen || forceOpen) && (
          <div
            className={classnames(POPUP_CLASSNAME, {
              dateRangeContainerLeft:
                this.isFieldSelected('startDate') ||
                (this.isFieldSelected('endDate') && type === 'before'),
              dateRangeContainerRight: this.isFieldSelected('endDate') && type !== 'before',
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
    if (!(evt?.target as HTMLElement)?.closest('.' + POPUP_CLASSNAME)) {
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
