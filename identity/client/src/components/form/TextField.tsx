/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { ChangeEvent, FC, useId } from "react";
import { PasswordInput, TextArea, TextInput } from "@carbon/react";
import useTranslate from "src/utility/localization";

type TextInputProps = {
  type?: "text" | "email";
  cols?: never;
};

type TextAreaProps = {
  type?: never;
  cols: number;
};

type PasswordInputProps = {
  type: "password";
  cols?: never;
};

export type TextFieldProps = {
  label: string;
  value: string;
  errors?: string[];
  helperText?: string;
  placeholder?: string;
  cols?: number;
  autoFocus?: boolean;
  onBlur?: () => void;
  readOnly?: boolean;
  onChange?: (newValue: string) => void;
  maxCount?: number;
  enableCounter?: boolean;
  counterMode?: "character" | "word";
} & (TextInputProps | TextAreaProps | PasswordInputProps);

const TextField: FC<TextFieldProps> = ({
  onChange,
  onBlur,
  errors = [],
  value,
  helperText,
  placeholder,
  label,
  cols,
  autoFocus = false,
  type = "text",
  readOnly,
  maxCount = 255,
  enableCounter = false,
  counterMode = "character",
}) => {
  const { t } = useTranslate();
  const fieldId = useId();

  const commonProps = {
    labelText: label,
    title: label,
    id: fieldId,
    helperText: helperText,
    value: value,
    placeholder: placeholder,
    onChange: (
      e: ChangeEvent<HTMLInputElement> | React.ChangeEvent<HTMLTextAreaElement>,
    ) => onChange?.(e.currentTarget.value),
    invalid: errors && errors.length > 0,
    invalidText: errors?.map((e) => t(e)).join(" "),
    onBlur: onBlur,
    readOnly: readOnly,
    maxCount: maxCount,
    enableCounter: enableCounter,
    counterMode: counterMode,
    ...(autoFocus && { "data-modal-primary-focus": true }),
  };

  if (type === "password") {
    return <PasswordInput {...commonProps} />;
  } else if (cols && cols > 1) {
    return <TextArea {...commonProps} />;
  } else {
    return <TextInput {...commonProps} type={type} />;
  }
};

export default TextField;
