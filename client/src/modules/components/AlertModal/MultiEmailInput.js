/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {MultiValueInput} from 'components';

export default function MultiEmailInput({emails, onChange, placeholder}) {
  const [errors, setErrors] = useState([]);

  function addEmail(value) {
    const trimmedValue = value.trim();

    if (trimmedValue) {
      const isValid = isValidEmail(trimmedValue);
      if (!isValid) {
        setErrors([...errors, trimmedValue]);
      }

      onChange([...emails, trimmedValue], isValid && errors.length === 0);
    }
  }

  function removeEmail(email, index) {
    const newEmails = emails.filter((_, i) => i !== index);
    const errorIndex = errors.indexOf(email);
    const newErrors = errors.filter((_, i) => i !== errorIndex);
    setErrors(newErrors);
    onChange(newEmails, newErrors.length === 0);
  }

  function triggerClear() {
    setErrors([]);
    onChange([], true);
  }

  function handlePaste(evt) {
    const paste = (evt.clipboardData || window.clipboardData).getData('text');
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

  return (
    <MultiValueInput
      placeholder={placeholder}
      disabled={emails.length >= 20}
      onClear={triggerClear}
      onPaste={handlePaste}
      onAdd={addEmail}
      onRemove={removeEmail}
      values={emails.map((email) => ({value: email, invalid: errors.includes(email)}))}
      extraSeperators={[',', ';', ' ']}
    />
  );
}

function isValidEmail(email) {
  return /^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$/.test(email);
}
