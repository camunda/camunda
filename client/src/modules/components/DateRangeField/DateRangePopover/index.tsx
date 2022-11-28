/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Form} from 'react-final-form';
import {DatePicker} from '@carbon/react';
import {Button} from 'modules/components/Button';
import {formatDate} from '../formatDate';
import {DateTimeInput} from './DateTimeInput';
import {DatePickerContainer, Footer, Popover} from './styled';
import {logger} from 'modules/logger';

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
  const [datePickerRef, setDatePickerRef] = useState<HTMLDivElement | null>(
    null
  );

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
      onOutsideClick={onOutsideClick}
      variant="arrow"
    >
      <Form onSubmit={handleApply} initialValues={defaultValues}>
        {({handleSubmit, form}) => (
          <form onSubmit={handleSubmit}>
            {datePickerRef !== null && (
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
                appendTo={datePickerRef}
                light
                inline
                short
              >
                <DateTimeInput
                  id="date-picker-input-id-start"
                  type="from"
                  labelText="From"
                  autoFocus
                />
                <DateTimeInput
                  id="date-picker-input-id-finish"
                  type="to"
                  labelText="To"
                />
              </DatePicker>
            )}
            <DatePickerContainer ref={(element) => setDatePickerRef(element)} />
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
