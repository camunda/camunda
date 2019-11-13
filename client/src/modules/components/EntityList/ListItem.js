/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import './ListItem.scss';

export default function ListItem({className, children, onClick}) {
  return (
    <li className={classnames('ListItem', className, {clickable: onClick})} onClick={onClick}>
      <div className="indicator" />
      <ul>{children}</ul>
    </li>
  );
}

ListItem.Section = function ListItemSection({className, ...props}) {
  return <li className={classnames('ListItemSection', className)} {...props} />;
};
