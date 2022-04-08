/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Icon} from 'components';

import './SelectionPreview.scss';

export default function SelectionPreview({disabled, onClick, highlighted, className, children}) {
  return (
    <span className={classnames('SelectionPreview', {highlighted}, {disabled}, className)}>
      {children}
      <Button disabled={disabled} onClick={onClick} icon>
        <Icon type="close-small" />
      </Button>
    </span>
  );
}
