/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Input, Message, Icon} from 'components';
import {t} from 'translation';

import './PickerDateInput.scss';

export const PickerDateInput = React.memo(
  ({onChange, onSubmit, reference, isInvalid, ...props}) => {
    const invalid = props.value && isInvalid;
    return (
      <div className="PickerDateInput">
        <Input
          type="text"
          {...props}
          isInvalid={invalid}
          placeholder="yyyy-mm-dd"
          onChange={({target: {value}}) => onChange(value)}
          onKeyDown={({key}) => key === 'Enter' && onSubmit()}
          ref={reference}
        />
        <Icon type="calender" />
        {invalid && (
          <Message error className="PickerDateInputWarning">
            {t('common.filter.dateModal.invalidDate')}
          </Message>
        )}
      </div>
    );
  }
);

export default React.forwardRef((props, ref) => <PickerDateInput {...props} reference={ref} />);
