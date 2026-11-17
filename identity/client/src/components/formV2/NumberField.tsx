/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useState } from "react";
import { Input } from "@camunda/design-system";
import FormField from "./FormField";

export type NumberFieldProps = {
  label: string;
  value: number | undefined;
  onValueChange: (value: number) => void;
  error?: string;
  helperText?: string;
  min?: number;
  step?: number;
};

/**
 * The design system has no `NumberInput` counterpart, so this composes `Input`
 * with `type="number"`.
 */
const NumberField: FC<NumberFieldProps> = ({
  label,
  value,
  onValueChange,
  error,
  helperText,
  min,
  step,
}) => {
  // A plain controlled number input snaps back to the last valid value the
  // moment the field is emptied, so a reviewer wondering why the draft state
  // exists: without it you cannot clear the field and retype a number.
  const [draft, setDraft] = useState<string | null>(null);

  return (
    <FormField label={label} error={error} helperText={helperText}>
      {(control) => (
        <Input
          {...control}
          type="number"
          title={label}
          min={min}
          step={step}
          value={draft ?? (value === undefined ? "" : String(value))}
          onChange={(event) => {
            const raw = event.currentTarget.value;
            setDraft(raw);
            const parsed = Number.parseInt(raw, 10);
            if (!Number.isNaN(parsed)) {
              onValueChange(parsed);
            }
          }}
          onBlur={() => setDraft(null)}
        />
      )}
    </FormField>
  );
};

export default NumberField;
