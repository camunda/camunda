/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Icon, Dropdown, Button, Tooltip} from 'components';

import './ListItemAction.scss';

export default function ListItemAction({actions = [], singleAction}) {
  if (!actions || actions.length === 0) {
    return <div className="ListItemAction" />;
  }

  if (singleAction) {
    const {icon, action, text} = actions[0];
    return (
      <Tooltip content={text} align="right">
        <Button
          icon
          className="ListItemAction"
          onClick={(evt) => {
            evt.preventDefault();
            action(evt);
          }}
        >
          <Icon type={icon} />
        </Button>
      </Tooltip>
    );
  }

  return (
    <Dropdown className="ListItemAction" icon label={<Icon type="context-menu" />}>
      {actions.map(({action, icon, text}, idx) => (
        <Dropdown.Option onClick={action} key={idx}>
          <Icon type={icon} /> {text}
        </Dropdown.Option>
      ))}
    </Dropdown>
  );
}
