/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import classnames from 'classnames';
import {Button} from '@carbon/react';
import {Close} from '@carbon/icons-react';

import {t} from 'translation';

import './SelectionPreview.scss';

export default function SelectionPreview({disabled, onClick, highlighted, className, children}) {
  return (
    <span
      className={classnames(
        'SelectionPreview',
        'cds--text-input',
        'cds--text-input__field-wrapper',
        {highlighted},
        {disabled},
        className
      )}
    >
      {children}
      <Button
        size="sm"
        renderIcon={Close}
        kind="ghost"
        hasIconOnly
        disabled={disabled}
        onClick={onClick}
        iconDescription={t('common.remove')}
        className="closeBtn"
      />
    </span>
  );
}
