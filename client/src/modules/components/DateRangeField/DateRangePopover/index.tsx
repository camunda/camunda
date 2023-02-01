/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {Form} from 'react-final-form';
import {DatePicker} from '@carbon/react';
import {logger} from 'modules/logger';
import {Button} from 'modules/components/Button';
import {formatDate} from '../formatDate';
import {DateInput} from './DateInput';
import {TimeInput} from './TimeInput';
import {
  Body,
  DatePickerContainer,
  TimeInputContainer,
  Footer,
  Popover,
  Title,
} from './styled';
import {tracking} from 'modules/tracking';
import {hide, offset, shift} from '@floating-ui/react-dom';

const defaultTime = {
  from: '00:00:00',
  to: '23:59:59',
};

type Props = {
  title: string;
  referenceElement: HTMLElement;
  filterName: string;
  onCancel: () => void;
  onOutsideClick?: (event: MouseEvent) => void;
  onApply: ({
    fromDateTime,
    toDateTime,
  }: {
    fromDateTime: Date;
    toDateTime: Date;
  }) => void;
  defaultValues: {
    fromDate: string;
    fromTime: string;
    toDate: string;
    toTime: string;
  };
};

const DateRangePopover: React.FC<Props> = ({
  title,
  referenceElement,
  filterName,
  onCancel,
  onApply,
  onOutsideClick,
  defaultValues,
}) => {
  const [calendarRef, setCalendarRef] = useState<Element | null>(null);

  const handleOutsideClick = (event: MouseEvent) => {
    // this prevents the popover from closing
    // when the user clicks on the calendar
    if (
      event.target instanceof Element &&
      calendarRef?.contains(event.target)
    ) {
      return;
    }
    onOutsideClick?.(event);
  };

  const [dateSelectionMethods, setDateSelectionMethods] = useState<{
    datePicker: boolean;
    dateInput: boolean;
    quickFilter: boolean;
  }>({datePicker: false, dateInput: false, quickFilter: false});

  useEffect(() => {
    const flatpickrDays = calendarRef?.querySelector('.flatpickr-days');
    const handlePick = () => {
      setDateSelectionMethods((prevState) => ({
        ...prevState,
        datePicker: true,
      }));
    };

    flatpickrDays?.addEventListener('click', handlePick);
    return () => flatpickrDays?.removeEventListener('click', handlePick);
  }, [calendarRef]);

  const handleApply = ({
    fromDate,
    fromTime,
    toDate,
    toTime,
  }: {
    fromDate?: string;
    fromTime?: string;
    toDate?: string;
    toTime?: string;
  }) => {
    if (
      fromDate !== undefined &&
      fromTime !== undefined &&
      toDate !== undefined &&
      toTime !== undefined
    ) {
      try {
        onApply({
          fromDateTime: new Date(`${fromDate} ${fromTime}`),
          toDateTime: new Date(`${toDate} ${toTime}`),
        });
        tracking.track({
          eventName: 'date-range-applied',
          methods: {
            ...dateSelectionMethods,
            timeInput:
              fromTime !== defaultTime.from || toTime !== defaultTime.to,
          },
          filterName,
        });
      } catch (e) {
        logger.error(e);
      }
    }
  };

  return (
    <Popover
      referenceElement={referenceElement}
      placement="right-start"
      onOutsideClick={handleOutsideClick}
      variant="arrow"
      middlewareOptions={[offset(10), shift(), hide()]}
      autoUpdatePosition
    >
      <Form onSubmit={handleApply} initialValues={defaultValues}>
        {({handleSubmit, form}) => (
          <form onSubmit={handleSubmit}>
            <Body>
              <Title>{title}</Title>
              <DatePickerContainer>
                <DatePicker
                  datePickerType="range"
                  onChange={(event) => {
                    const [fromDateTime, toDateTime] = event;
                    if (fromDateTime !== undefined) {
                      form.change('fromDate', formatDate(fromDateTime));
                      if (form.getFieldState('fromTime')?.value === '') {
                        form.change('fromTime', defaultTime.from);
                      }
                    }
                    if (toDateTime !== undefined) {
                      form.change('toDate', formatDate(toDateTime));
                      if (form.getFieldState('toTime')?.value === '') {
                        form.change('toTime', defaultTime.to);
                      }
                    }
                  }}
                  dateFormat="Y-m-d"
                  short
                  onOpen={() => {
                    setCalendarRef(
                      document.querySelector('.flatpickr-calendar')
                    );
                  }}
                  onClose={() => setCalendarRef(null)}
                >
                  <DateInput
                    id="date-picker-input-id-start"
                    type="from"
                    labelText="From date"
                    onChange={() =>
                      setDateSelectionMethods((prevState) => ({
                        ...prevState,
                        dateInput: true,
                      }))
                    }
                  />
                  <DateInput
                    id="date-picker-input-id-finish"
                    type="to"
                    labelText="To date"
                    onChange={() =>
                      setDateSelectionMethods((prevState) => ({
                        ...prevState,
                        dateInput: true,
                      }))
                    }
                  />
                </DatePicker>
              </DatePickerContainer>

              <TimeInputContainer>
                <TimeInput
                  type="from"
                  labelText="From time"
                  onChange={() =>
                    setDateSelectionMethods((prevState) => ({
                      ...prevState,
                      timeInput: true,
                    }))
                  }
                />
                <TimeInput
                  type="to"
                  labelText="To time"
                  onChange={() =>
                    setDateSelectionMethods((prevState) => ({
                      ...prevState,
                      timeInput: true,
                    }))
                  }
                />
              </TimeInputContainer>
            </Body>
            <Footer>
              <Button
                type="reset"
                color="secondary"
                size="medium"
                title="Cancel"
                onClick={onCancel}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                color="primary"
                size="medium"
                title="Apply"
                disabled={
                  !form.getFieldState('fromDate')?.value ||
                  !form.getFieldState('fromTime')?.value ||
                  !form.getFieldState('toDate')?.value ||
                  !form.getFieldState('toTime')?.value ||
                  form.getState().invalid
                }
              >
                Apply
              </Button>
            </Footer>
          </form>
        )}
      </Form>
    </Popover>
  );
};

export {DateRangePopover};
