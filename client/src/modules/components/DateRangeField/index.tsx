/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef, useState} from 'react';
import {Field, useField, useForm} from 'react-final-form';
import {DateRangePopover} from './DateRangePopover';
import {formatDate, formatISODate} from './formatDate';
import {TextField} from './styled';

type Props = {
  label: string;
  fromDateKey: string;
  toDateKey: string;
};

const formatInputValue = (fromDate?: Date, toDate?: Date) => {
  if (fromDate === undefined || toDate === undefined) {
    return '';
  }
  return `${formatDate(fromDate)} - ${formatDate(toDate)}`;
};

const DateRangeField: React.FC<Props> = ({label, fromDateKey, toDateKey}) => {
  const cmTextFieldRef = useRef<HTMLCmTextfieldElement>(null);
  const textFieldRef = useRef<HTMLDivElement>(null);
  const form = useForm();
  const fromDate = useField<string>(fromDateKey).input.value;
  const toDate = useField<string>(toDateKey).input.value;

  const [isDateRangePopoverVisible, setIsDateRangePopoverVisible] =
    useState<boolean>(false);

  const getInputValue = () => {
    if (isDateRangePopoverVisible) {
      return 'Custom';
    }
    if (fromDate !== '' && toDate !== '') {
      return formatInputValue(new Date(fromDate), new Date(toDate));
    }
    return '';
  };

  const handleCancel = () => {
    setIsDateRangePopoverVisible(false);
  };

  return (
    <>
      <div ref={textFieldRef}>
        <TextField
          label={label}
          type="button"
          fieldSuffix={{
            type: 'icon',
            icon: 'calendar',
            press: () => {},
          }}
          value={getInputValue()}
          ref={cmTextFieldRef}
          readonly
          title={getInputValue()}
          onCmClick={() => {
            if (!isDateRangePopoverVisible) {
              setIsDateRangePopoverVisible(true);
            }
          }}
        />
        {[fromDateKey, toDateKey].map((filterKey) => (
          <Field
            name={filterKey}
            key={filterKey}
            component="input"
            type="hidden"
          />
        ))}
      </div>

      {isDateRangePopoverVisible && textFieldRef.current !== null && (
        <DateRangePopover
          referenceElement={textFieldRef.current}
          onCancel={handleCancel}
          onApply={({fromDate, toDate}) => {
            setIsDateRangePopoverVisible(false);
            form.change(fromDateKey, formatISODate(fromDate));
            form.change(toDateKey, formatISODate(toDate));
          }}
          onOutsideClick={(event) => {
            if (
              event.target instanceof Element &&
              cmTextFieldRef.current?.contains(event.target)
            ) {
              event.stopPropagation();
              event.preventDefault();
            }
            handleCancel();
          }}
          defaultValues={{fromDate, toDate}}
        />
      )}
    </>
  );
};

export {DateRangeField};
