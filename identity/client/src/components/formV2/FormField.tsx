/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode, useId } from "react";
import { Label } from "@camunda/design-system";

export type FormFieldControlProps = {
  id: string;
  "aria-describedby": string | undefined;
};

export type FormFieldProps = {
  label: string;
  /** Receives the id it has to carry, which is part of the control's `aria-describedby`. */
  footer?: (id: string) => ReactNode;
  children: (control: FormFieldControlProps) => ReactNode;
};

/**
 * Label and control, with the id wiring between them. `Input`, `Textarea`,
 * and `SelectTrigger` render their own error/helper text and `aria-*` wiring
 * given `invalidText`/`helperText`/`aria-invalid` directly — pass those to
 * the control instead of through this wrapper. `MultiSelect` has no such
 * support, so its callers must render their own error text alongside it.
 */
const FormField: FC<FormFieldProps> = ({ label, footer, children }) => {
  const id = useId();
  const footerId = `${id}-footer`;

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children({
        id,
        "aria-describedby": footer ? footerId : undefined,
      })}
      {footer?.(footerId)}
    </div>
  );
};

export default FormField;
