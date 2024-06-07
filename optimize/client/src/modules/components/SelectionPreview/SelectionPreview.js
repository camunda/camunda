/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
