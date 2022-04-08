/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button} from 'components';

import {t} from 'translation';

export default function CopyToClipboard({children, value, disabled, onCopy}) {
  return (
    <Button
      className="CopyToClipboard"
      onClick={(evt) => {
        evt.preventDefault();
        const input = document.createElement('input');
        input.value = value;
        input.style.opacity = 0;
        input.style.position = 'absolute';
        input.style.top = 0;

        document.body.appendChild(input);

        input.select();
        document.execCommand('Copy');

        document.body.removeChild(input);

        if (typeof onCopy === 'function') {
          onCopy();
        }
      }}
      disabled={disabled}
    >
      {children || t('common.copy')}
    </Button>
  );
}
