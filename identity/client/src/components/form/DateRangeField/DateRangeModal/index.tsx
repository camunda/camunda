/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { DatePicker, Layer, Modal, Stack } from "@carbon/react";
import { formatDate } from "../formatDate";
import { DateInput } from "./DateInput";
import { TimeInput } from "./TimeInput";
import { TimeInputStack } from "./styled";
import { createPortal } from "react-dom";
import { FormProvider, useForm } from "react-hook-form";

const defaultTime = {
  from: "00:00:00",
  to: "23:59:59",
};

type FormValues = {
  fromDate: string;
  fromTime: string;
  toDate: string;
  toTime: string;
};

type Props = {
  title: string;
  onCancel: () => void;
  onApply: ({
    fromDateTime,
    toDateTime,
  }: {
    fromDateTime: Date;
    toDateTime: Date;
  }) => void;
  defaultValues: FormValues;
  isModalOpen: boolean;
};

const DateRangeModal: React.FC<Props> = ({
  defaultValues,
  onApply,
  onCancel,
  title,
  isModalOpen,
}) => {
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
        console.error(e);
      }
    }
  };

  const methods = useForm<FormValues>({
    defaultValues,
  });

  return (
    <FormProvider {...methods}>
      <form>
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
                onRequestSubmit={methods.handleSubmit(handleApply)}
                primaryButtonDisabled={!methods.formState.isValid}
              >
                <Stack gap={6}>
                  <div>
                    <DatePicker
                      datePickerType="range"
                      onChange={(event) => {
                        const [fromDateTime, toDateTime] = event;
                        if (fromDateTime !== undefined) {
                          methods.setValue(
                            "fromDate",
                            formatDate(fromDateTime),
                          );
                          if (methods.getValues("fromTime") === "") {
                            methods.setValue("fromTime", defaultTime.from);
                          }
                        }
                        if (toDateTime !== undefined) {
                          methods.setValue("toDate", formatDate(toDateTime));
                          if (methods.getValues("toTime") === "") {
                            methods.setValue("toTime", defaultTime.to);
                          }
                        }
                      }}
                      dateFormat="Y-m-d"
                      short
                    >
                      <DateInput
                        id="date-picker-input-id-start"
                        type="from"
                        labelText="From date"
                      />
                      <DateInput
                        id="date-picker-input-id-finish"
                        type="to"
                        labelText="To date"
                      />
                    </DatePicker>
                  </div>
                  <TimeInputStack orientation="horizontal">
                    <TimeInput type="from" labelText="From time" />
                    <TimeInput type="to" labelText="To time" />
                  </TimeInputStack>
                </Stack>
              </Modal>,
              document.body,
            )}
          </>
        </Layer>
      </form>
    </FormProvider>
  );
};

export { DateRangeModal };
