/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Button, Message, Icon} from 'components';

import './ActionItem.scss';

export default function ActionItem({onClick, type, warning, onEdit, ...props}) {
  return (
    <div className="ActionItem">
      <div className="header">
        {type && <div className="type">{type}</div>}
        <div className="buttons">
          {onEdit && (
            <Button onClick={onEdit} icon>
              <Icon size="15" type="edit" />
            </Button>
          )}
          <Button onClick={onClick} icon>
            <Icon size="15" type="close-large" />
          </Button>
        </div>
      </div>
      <div {...props} className={classnames('content', props.className)}>
        {props.children}
      </div>
      {warning && (
        <Message error>
          <Icon type="warning" /> {warning}
        </Message>
      )}
    </div>
  );
}
