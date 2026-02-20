/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { forwardRef } from "react";
import { Controller, useFormContext } from "react-hook-form";
import { DatePickerInput } from "@carbon/react";

type Props = {
  type: "from" | "to";
  id: string;
  labelText: string;
  onChange?: () => void;
  autoFocus?: boolean;
};

export const DateInput = forwardRef<HTMLDivElement, Props>(
  ({ type, onChange, ...props }, ref) => {
    const { control } = useFormContext();

    const fieldName = `${type}Date` as const;

    return (
      <Controller
        name={fieldName}
        control={control}
        render={({ field }) => (
          <DatePickerInput
            {...props}
            size="sm"
            value={field.value ?? ""}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
              field.onChange(event.target.value);
              onChange?.();
            }}
            ref={ref}
            placeholder="YYYY-MM-DD"
            // @ts-expect-error - Carbon types are wrong
            pattern="\\d{4}-\\d{1,2}-\\d{1,2}"
            maxLength={10}
            autoComplete="off"
          />
        )}
      />
    );
  },
);
