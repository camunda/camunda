/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ChangeEvent, FC, FocusEvent, ReactNode, useState } from "react";
import {
  Button,
  CharacterCount,
  Input,
  Textarea,
} from "@camunda/design-system";
import { Eye, EyeOff } from "lucide-react";
import useTranslate from "src/utility/localization";
import FormField from "./FormField";

type TextInputProps = {
  type?: "text" | "email";
  cols?: never;
  counterMode?: never;
  enableCounter?: never;
  maxCount?: never;
};

type TextAreaProps = {
  type?: never;
  cols: number;
  counterMode?: "character" | "word";
  enableCounter?: boolean;
  maxCount?: number;
};

type PasswordInputProps = {
  type: "password";
  cols?: never;
  counterMode?: never;
  enableCounter?: never;
  maxCount?: never;
};

export type TextFieldProps = {
  label: string;
  value: string;
  errors?: string[] | string;
  helperText?: ReactNode;
  placeholder?: string;
  cols?: number;
  autoFocus?: boolean;
  onBlur?: (newValue: string) => void;
  readOnly?: boolean;
  onChange?: (newValue: string) => void;
  validate?: (newValue: string) => boolean;
  name?: string;
  autoComplete?: string;
} & (TextInputProps | TextAreaProps | PasswordInputProps);

const TextField: FC<TextFieldProps> = ({
  onChange,
  onBlur,
  validate,
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
  name,
  autoComplete,
}) => {
  const { t } = useTranslate();
  const [passwordVisible, setPasswordVisible] = useState(false);

  const errorText =
    typeof errors === "string" ? errors : errors.map((e) => t(e)).join(" ");

  const isTextArea = type !== "password" && Boolean(cols && cols > 1);
  const showCounter = isTextArea && enableCounter;

  const handleChange = (
    e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    onChange?.(e.currentTarget.value);
    validate?.(e.currentTarget.value);
  };

  const handleBlur = (
    e: FocusEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    onBlur?.(e.currentTarget.value);
    validate?.(e.currentTarget.value);
  };

  return (
    <FormField
      label={label}
      error={errorText}
      helperText={helperText}
      footer={
        showCounter
          ? (id) => (
              <CharacterCount
                id={id}
                value={value}
                max={maxCount}
                mode={counterMode}
                className="self-end"
              />
            )
          : undefined
      }
    >
      {(control) => {
        const commonProps = {
          ...control,
          title: label,
          value,
          placeholder,
          readOnly,
          autoFocus,
          name,
          autoComplete,
          onChange: handleChange,
          onBlur: handleBlur,
        };

        if (type === "password") {
          return (
            <div className="relative">
              <Input
                {...commonProps}
                type={passwordVisible ? "text" : "password"}
                className="pr-10"
              />
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                className="absolute inset-y-0 right-1 my-auto"
                aria-label={
                  passwordVisible ? t("hidePassword") : t("showPassword")
                }
                aria-pressed={passwordVisible}
                onClick={() => setPasswordVisible((visible) => !visible)}
              >
                {passwordVisible ? (
                  <EyeOff aria-hidden="true" />
                ) : (
                  <Eye aria-hidden="true" />
                )}
              </Button>
            </div>
          );
        }

        if (isTextArea) {
          return (
            <Textarea
              {...commonProps}
              cols={cols}
              // Character mode caps input through the native attribute. Word
              // mode has no native equivalent, so it counts without capping —
              // Carbon enforced that limit in JS, the design system does not.
              maxLength={
                showCounter && counterMode === "character"
                  ? maxCount
                  : undefined
              }
            />
          );
        }

        return <Input {...commonProps} type={type} />;
      }}
    </FormField>
  );
};

export default TextField;
