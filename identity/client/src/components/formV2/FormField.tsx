/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode, useId } from "react";
import { Label, Text } from "@camunda/design-system";

export type FormFieldControlProps = {
  id: string;
  "aria-invalid": true | undefined;
  "aria-describedby": string | undefined;
};

export type FormFieldProps = {
  label: string;
  error?: ReactNode;
  helperText?: ReactNode;
  /** Receives the id it has to carry, which is part of the control's `aria-describedby`. */
  footer?: (id: string) => ReactNode;
  children: (control: FormFieldControlProps) => ReactNode;
};

/**
 * Label, control, and error/helper text, with the `aria-*` wiring between
 * them. The design system ships the parts but no wrapper that connects them.
 *
 * The control is a render prop because only the caller knows which element the
 * generated id and `aria-*` attributes belong on. A control that accepts
 * neither `aria-invalid` nor `aria-describedby` — `MultiSelect` — can take the
 * `id` alone; its error still announces through `role="alert"` below.
 */
const FormField: FC<FormFieldProps> = ({
  label,
  error,
  helperText,
  footer,
  children,
}) => {
  const id = useId();
  const errorId = `${id}-error`;
  const helperId = `${id}-helper`;
  const footerId = `${id}-footer`;

  const showHelperText = !error && Boolean(helperText);
  const describedBy =
    [
      error ? errorId : undefined,
      showHelperText ? helperId : undefined,
      footer ? footerId : undefined,
    ]
      .filter(Boolean)
      .join(" ") || undefined;

  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children({
        id,
        "aria-invalid": error ? true : undefined,
        "aria-describedby": describedBy,
      })}
      {error || showHelperText ? (
        <Text
          as="p"
          variant="helper"
          id={error ? errorId : helperId}
          role={error ? "alert" : undefined}
          className={error ? "text-danger-action-default" : undefined}
        >
          {error || helperText}
        </Text>
      ) : null}
      {footer?.(footerId)}
    </div>
  );
};

export default FormField;
