/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { DateRangePicker } from "@camunda/design-system";
import { parseISO } from "date-fns";
import { FormModal } from "src/components/modalV2";
import TextField from "src/components/formV2/TextField";
import { formatDate } from "../formatDate";
import { validateTimeRange } from "../validators";
import { Controller, useForm } from "react-hook-form";

const defaultTime = {
  from: "00:00:00",
  to: "23:59:59",
};

export type FormValues = {
  fromDate: string;
  toDate: string;
  fromTime: string;
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
    mode: "onChange",
    reValidateMode: "onChange",
  });

  const selectedRange =
    defaultValues.fromDate !== "" && defaultValues.toDate !== ""
      ? {
          from: parseISO(defaultValues.fromDate),
          to: parseISO(defaultValues.toDate),
        }
      : undefined;

  return (
    <FormModal
      open={isModalOpen}
      headline={title}
      size="sm"
      confirmLabel="Apply"
      submitDisabled={!methods.formState.isValid}
      onClose={onCancel}
      onSubmit={methods.handleSubmit(handleApply)}
    >
      <DateRangePicker
        defaultValue={selectedRange}
        onChange={(range) => {
          if (range?.from) {
            methods.setValue("fromDate", formatDate(range.from), {
              shouldValidate: true,
            });
            if (methods.getValues("fromTime") === "") {
              methods.setValue("fromTime", defaultTime.from, {
                shouldValidate: true,
              });
            }
          }
          if (range?.to) {
            methods.setValue("toDate", formatDate(range.to), {
              shouldValidate: true,
            });
            if (methods.getValues("toTime") === "") {
              methods.setValue("toTime", defaultTime.to, {
                shouldValidate: true,
              });
            }
          }
        }}
      />
      <div className="grid grid-cols-2 gap-4">
        <Controller
          name="fromTime"
          control={methods.control}
          rules={{ validate: { range: validateTimeRange } }}
          render={({ field, fieldState }) => (
            <TextField
              {...field}
              type="time"
              label="From time"
              errors={fieldState.error?.message}
            />
          )}
        />
        <Controller
          name="toTime"
          control={methods.control}
          rules={{ validate: { range: validateTimeRange } }}
          render={({ field, fieldState }) => (
            <TextField
              {...field}
              type="time"
              label="To time"
              errors={fieldState.error?.message}
            />
          )}
        />
      </div>
    </FormModal>
  );
};

export { DateRangeModal };
