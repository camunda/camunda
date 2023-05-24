/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {Form} from 'react-final-form';
import {DatePicker, Layer, Modal, Stack} from '@carbon/react';
import {logger} from 'modules/logger';
import {formatDate} from '../formatDate';
import {DateInput} from './DateInput';
import {TimeInput} from './TimeInput';
import {TimeInputStack} from './styled';
import {tracking} from 'modules/tracking';
import {createPortal} from 'react-dom';

const defaultTime = {
  from: '00:00:00',
  to: '23:59:59',
};

type Props = {
  title: string;
  filterName: string;
  onCancel: () => void;
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
  isModalOpen: boolean;
};

const DateRangeModal: React.FC<Props> = ({
  defaultValues,
  onApply,
  onCancel,
  filterName,
  title,
  isModalOpen,
}) => {
  const [calendarRef, setCalendarRef] = useState<Element | null>(null);
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
    <Form onSubmit={handleApply} initialValues={defaultValues}>
      {({handleSubmit, form}) => (
        <Layer level={0}>
          <>
            {createPortal(
              <Modal
                data-testid="date-range-modal"
                open={isModalOpen}
                size="xs"
                modalHeading={title}
                primaryButtonText="Apply"
                secondaryButtonText="Cancel"
                onRequestClose={onCancel}
                onRequestSubmit={handleSubmit}
                primaryButtonDisabled={
                  !form.getFieldState('fromDate')?.value ||
                  !form.getFieldState('fromTime')?.value ||
                  !form.getFieldState('toDate')?.value ||
                  !form.getFieldState('toTime')?.value ||
                  form.getState().invalid
                }
              >
                <Stack gap={6}>
                  <div>
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
                  </div>
                  <TimeInputStack orientation="horizontal">
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
                  </TimeInputStack>
                </Stack>
              </Modal>,
              document.body
            )}
          </>
        </Layer>
      )}
    </Form>
  );
};

export {DateRangeModal};
