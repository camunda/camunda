/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { TextInput } from "@carbon/react";
import {
  validateTimeCharacters,
  validateTimeComplete,
  validateTimeRange,
} from "src/components/form/DateRangeField/validators.ts";
import { Controller, useFormContext } from "react-hook-form";

type Props = {
  type: "from" | "to";
  labelText: string;
  onChange?: () => void;
};

type DateFieldName = `${"from" | "to"}Time`;

export type FormValues = {
  fromDate: string;
  toDate: string;
  fromTime: string;
  toTime: string;
};

const TimeInput: React.FC<Props> = ({ type, labelText, onChange }) => {
  const { control, getValues } = useFormContext<FormValues>();

  const fieldName: DateFieldName = `${type}Time`;

  return (
    <Controller
      name={fieldName}
      control={control}
      rules={{
        validate: {
          complete: validateTimeComplete,
          characters: validateTimeCharacters,
          range: validateTimeRange(getValues),
        },
      }}
      render={({ field, fieldState }) => (
        <TextInput
          {...field}
          id="time-picker"
          labelText={labelText}
          size="sm"
          placeholder="hh:mm:ss"
          data-testid={fieldName}
          maxLength={8}
          autoComplete="off"
          invalid={!!fieldState.error}
          invalidText={fieldState.error?.message}
          onChange={(event) => {
            field.onChange(event.target.value);
            onChange?.();
          }}
        />
      )}
    />
  );
};

export { TimeInput };
