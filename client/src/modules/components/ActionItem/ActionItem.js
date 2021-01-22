/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Message, Icon} from 'components';

import './ActionItem.scss';

export default function ActionItem({disabled, onClick, highlighted, warning, ...props}) {
  return (
    <div className={classnames({highlighted}, 'ActionItem')}>
      <span {...props} className={classnames('content', props.className)}>
        {props.children}
      </span>
      {warning && (
        <Message error>
          <Icon type="warning" /> {warning}
        </Message>
      )}
      <Button disabled={disabled} onClick={onClick}>
        ×
      </Button>
    </div>
  );
}
