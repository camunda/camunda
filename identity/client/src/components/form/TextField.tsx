/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { ChangeEvent, FC } from "react";
import { TextArea, TextInput } from "@carbon/react";
import useTranslate from "src/utility/localization";

export type TextFieldProps = {
  label: string;
  onChange: (newValue: string) => void;
  value: string;
  errors?: string[];
  helperText?: string;
  placeholder?: string;
  cols?: number;
  autofocus?: boolean;
};

const TextField: FC<TextFieldProps> = ({
  onChange,
  errors,
  value,
  helperText,
  placeholder,
  label,
  cols,
  autofocus = false,
}) => {
  const { t } = useTranslate();
  const tMapper = (d: string | undefined): string => {
    return d != undefined ? t(d) : "";
  };
  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    onChange(e.currentTarget.value);
  };
  const InputComponent = cols && cols > 1 ? TextArea : TextInput;

  const additionalProps = autofocus
    ? {
        "data-modal-primary-focus": true,
      }
    : {};

  return (
    <InputComponent
      labelText={label}
      title={label}
      id={label}
      helperText={helperText}
      value={value}
      placeholder={placeholder}
      onChange={handleChange}
      invalid={errors && errors.length > 0}
      invalidText={errors?.map(tMapper).join(" ")}
      {...additionalProps}
    />
  );
};

export default TextField;
