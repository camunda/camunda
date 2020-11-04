/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {Input, Icon, Tooltip} from 'components';

import ListItemAction from './ListItemAction';

import './ListItem.scss';

export default function ListItem({
  data: {type, name, link, icon, meta = [], actions, warning},
  hasWarning,
  singleAction,
}) {
  const content = (
    <>
      {' '}
      <Input type="checkbox" />
      <Icon type={icon} />
      <div className="name">
        <span className="type">{type}</span>
        <Tooltip content={name} overflowOnly>
          <span className="entity">{name}</span>
        </Tooltip>
      </div>
      {meta.map((content, idx) => (
        <Tooltip key={idx} content={content} overflowOnly>
          <div className="meta">{content}</div>
        </Tooltip>
      ))}
      {hasWarning && (
        <div className="warning">
          {warning && (
            <Tooltip content={warning} theme="dark" delay={0}>
              <Icon type="error" size="18" />
            </Tooltip>
          )}
        </div>
      )}
      <ListItemAction actions={actions} singleAction={singleAction} />
    </>
  );

  return (
    <li className="ListItem">{link ? <Link to={link}>{content}</Link> : <div>{content}</div>}</li>
  );
}
