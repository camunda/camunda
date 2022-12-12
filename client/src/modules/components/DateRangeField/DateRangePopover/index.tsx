/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Form} from 'react-final-form';
import {DatePicker} from '@carbon/react';
import {useState} from 'react';
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
} from './styled';

type Props = {
  referenceElement: HTMLElement;
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
  referenceElement,
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
      } catch (e) {
        logger.error(e);
      }
    }
  };

  return (
    <Popover
      referenceElement={referenceElement}
      offsetOptions={[10]}
      placement="right-start"
      onOutsideClick={handleOutsideClick}
      variant="arrow"
    >
      <Form onSubmit={handleApply} initialValues={defaultValues}>
        {({handleSubmit, form}) => (
          <form onSubmit={handleSubmit}>
            <Body>
              <DatePickerContainer>
                <DatePicker
                  datePickerType="range"
                  onChange={(event) => {
                    const [fromDateTime, toDateTime] = event;
                    if (fromDateTime !== undefined) {
                      form.change('fromDate', formatDate(fromDateTime));
                      if (form.getFieldState('fromTime')?.value === '') {
                        form.change('fromTime', '00:00:00');
                      }
                    }
                    if (toDateTime !== undefined) {
                      form.change('toDate', formatDate(toDateTime));
                      if (form.getFieldState('toTime')?.value === '') {
                        form.change('toTime', '23:59:59');
                      }
                    }
                  }}
                  dateFormat="Y-m-d"
                  light
                  short
                  style={{display: 'flex', flexDirection: 'column'}}
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
                    labelText="From"
                    autoFocus
                  />
                  <DateInput
                    id="date-picker-input-id-finish"
                    type="to"
                    labelText="To"
                  />
                </DatePicker>
              </DatePickerContainer>

              <TimeInputContainer>
                <TimeInput type="from" />
                <TimeInput type="to" />
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
                  form.getFieldState('fromDate')?.value === '' ||
                  form.getFieldState('fromTime')?.value === '' ||
                  form.getFieldState('toDate')?.value === '' ||
                  form.getFieldState('toTime')?.value === ''
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
