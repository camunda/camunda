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
import {DateInput} from './DateInput';
import {DatePickerContainer, Footer, Popover} from './styled';

type Props = {
  referenceElement: HTMLElement;
  onCancel: () => void;
  onOutsideClick?: (event: MouseEvent) => void;
  onApply: ({fromDate, toDate}: {fromDate: Date; toDate: Date}) => void;
  defaultValues: {fromDate: string; toDate: string};
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
    toDate,
  }: {
    fromDate?: string;
    toDate?: string;
  }) => {
    if (fromDate !== undefined && toDate !== undefined) {
      onApply({fromDate: new Date(fromDate), toDate: new Date(toDate)});
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
                  const [fromDate, toDate] = event;
                  if (fromDate !== undefined) {
                    form.change('fromDate', formatDate(fromDate));
                  }
                  if (toDate !== undefined) {
                    form.change('toDate', formatDate(toDate));
                  }
                }}
                dateFormat="Y-m-d"
                appendTo={datePickerRef}
                allowInput={false}
                light
                inline
                short
              >
                <DateInput
                  id="date-picker-input-id-start"
                  type="from"
                  labelText="From"
                />
                <DateInput
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
                  form.getFieldState('fromDate')?.value === undefined ||
                  form.getFieldState('toDate')?.value === undefined
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
