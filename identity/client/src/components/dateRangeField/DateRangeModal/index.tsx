/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {DatePicker, Layer, Modal, Stack} from '@carbon/react';
import {formatDate} from '../formatDate';
import {DateInput} from './DateInput';
import {TimeInput} from './TimeInput';
import {TimeInputStack} from './styled';
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
  const [fromDate, setFromDate] = useState(defaultValues.fromDate);
  const [fromTime, setFromTime] = useState(defaultValues.fromTime);
  const [toDate, setToDate] = useState(defaultValues.toDate);
  const [toTime, setToTime] = useState(defaultValues.toTime);

  useEffect(() => {
    setFromDate(defaultValues.fromDate);
    setFromTime(defaultValues.fromTime);
    setToDate(defaultValues.toDate);
    setToTime(defaultValues.toTime);
  }, [defaultValues, isModalOpen]);

  useEffect(() => {
    const flatpickrDays = calendarRef?.querySelector('.flatpickr-days');
    const handlePick = () => {
      // Tracking can be added here if needed
    };

    flatpickrDays?.addEventListener('click', handlePick);
    return () => flatpickrDays?.removeEventListener('click', handlePick);
  }, [calendarRef]);

  const handleApply = () => {
    if (fromDate && fromTime && toDate && toTime) {
      try {
        onApply({
          fromDateTime: new Date(`${fromDate} ${fromTime}`),
          toDateTime: new Date(`${toDate} ${toTime}`),
        });
      } catch (e) {
        console.error(e);
      }
    }
  };

  const isValid = fromDate && fromTime && toDate && toTime;

  return (
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
            onRequestSubmit={handleApply}
            primaryButtonDisabled={!isValid}
          >
            <Stack gap={6}>
              <div>
                <DatePicker
                  datePickerType="range"
                  onChange={(event) => {
                    const [fromDateTime, toDateTime] = event;
                    if (fromDateTime !== undefined) {
                      setFromDate(formatDate(fromDateTime));
                      if (!fromTime) {
                        setFromTime(defaultTime.from);
                      }
                    }
                    if (toDateTime !== undefined) {
                      setToDate(formatDate(toDateTime));
                      if (!toTime) {
                        setToTime(defaultTime.to);
                      }
                    }
                  }}
                  dateFormat="Y-m-d"
                  short
                  onOpen={() => {
                    setCalendarRef(
                      document.querySelector('.flatpickr-calendar'),
                    );
                  }}
                  onClose={() => setCalendarRef(null)}
                >
                  <DateInput
                    id="date-picker-input-id-start"
                    type="from"
                    labelText="From date"
                    value={fromDate}
                    onChange={setFromDate}
                  />
                  <DateInput
                    id="date-picker-input-id-finish"
                    type="to"
                    labelText="To date"
                    value={toDate}
                    onChange={setToDate}
                  />
                </DatePicker>
              </div>
              <TimeInputStack orientation="horizontal">
                <TimeInput
                  type="from"
                  labelText="From time"
                  value={fromTime}
                  onChange={setFromTime}
                />
                <TimeInput
                  type="to"
                  labelText="To time"
                  value={toTime}
                  onChange={setToTime}
                />
              </TimeInputStack>
            </Stack>
          </Modal>,
          document.body,
        )}
      </>
    </Layer>
  );
};

export {DateRangeModal};

