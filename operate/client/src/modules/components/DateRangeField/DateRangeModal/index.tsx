/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
                          document.querySelector('.flatpickr-calendar'),
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
              document.body,
            )}
          </>
        </Layer>
      )}
    </Form>
  );
};

export {DateRangeModal};
