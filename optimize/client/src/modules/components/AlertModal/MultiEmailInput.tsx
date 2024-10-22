/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ClipboardEvent, ComponentProps, KeyboardEvent, useState} from 'react';
import {MultiValueInput} from '@camunda/camunda-optimize-composite-components';

import {getRandomId} from 'services';

interface MultiEmailInputProps
  extends Pick<
    ComponentProps<typeof MultiValueInput>,
    'titleText' | 'placeholder' | 'helperText' | 'invalid' | 'invalidText'
  > {
  emails: string[];
  onChange: (newEmails: string[], isValid: boolean) => void;
}

export default function MultiEmailInput({emails, onChange, ...rest}: MultiEmailInputProps) {
  const id = getRandomId();
  const [errors, setErrors] = useState<string[]>([]);
  const [value, setValue] = useState('');

  function addEmail(value: string) {
    const trimmedValue = value.trim();

    if (trimmedValue) {
      const isValid = isValidEmail(trimmedValue);
      if (!isValid) {
        setErrors([...errors, trimmedValue]);
      }

      onChange([...emails, trimmedValue], isValid && errors.length === 0);
    }
    setValue('');
  }

  function removeEmail(email: string, index: number) {
    const newEmails = emails.filter((_, i) => i !== index);
    const errorIndex = errors.indexOf(email);
    const newErrors = errors.filter((_, i) => i !== errorIndex);
    setErrors(newErrors);
    onChange(newEmails, newErrors.length === 0);
  }

  function handlePaste(evt: ClipboardEvent) {
    const paste = (
      evt.clipboardData ||
      // @ts-expect-error this type is missing in window global types
      window.clipboardData
    ).getData('text');
    if (!paste.includes('@')) {
      return;
    }
    evt.preventDefault();

    const newEmails = paste.match(/[^\s<>!?:;]+/g);

    if (newEmails) {
      if (emails.length + newEmails.length > 20) {
        newEmails.length = 20 - emails.length;
      }
      const invalidEmails = newEmails.filter((email) => !isValidEmail(email));
      setErrors([...errors, ...invalidEmails]);
      const allEmailsValid = errors.length === 0 && invalidEmails.length === 0;
      onChange([...emails, ...newEmails], allEmailsValid);
    }
  }

  const emailObjects = emails.map((email) => ({value: email, invalid: errors.includes(email)}));

  function handleKeyDown(evt: KeyboardEvent) {
    if (['Enter', 'Tab', ',', ';', ' '].includes(evt.key)) {
      if (value) {
        evt.preventDefault();
      }
      addEmail(value);
    }
    if (value === '' && evt.key === 'Backspace' && emailObjects.length > 0) {
      const lastElementIndex = emailObjects.length - 1;
      removeEmail(emailObjects[lastElementIndex]!.value, lastElementIndex);
    }
  }

  return (
    <MultiValueInput
      {...rest}
      id={id}
      value={value}
      onChange={({target: {value}}) => setValue(value)}
      disabled={emails.length >= 20}
      onPaste={handlePaste}
      onRemove={removeEmail}
      values={emailObjects}
      onKeyDown={handleKeyDown}
      onBlur={() => addEmail(value)}
    />
  );
}

function isValidEmail(email: string) {
  return /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/.test(email);
}
