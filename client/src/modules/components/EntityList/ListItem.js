/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {Input, Icon, Dropdown} from 'components';

import './ListItem.scss';

export default function ListItem({
  data: {type, name, link, icon, meta = [], actions = [], warning},
  hasWarning
}) {
  const content = (
    <>
      {' '}
      <Input type="checkbox" />
      <Icon type={icon} />
      <div className="name">
        <span className="type">{type}</span>
        <span className="entity" title={name}>
          {name}
        </span>
      </div>
      {meta.map((content, idx) => (
        <div className="meta" key={idx}>
          {content}
        </div>
      ))}
      {hasWarning && (
        <div className="warning">
          {warning && (
            <>
              <Icon type="error" size="18" />
              <div className="Tooltip dark">
                <div className="Tooltip__text-bottom">{warning}</div>
              </div>
            </>
          )}
        </div>
      )}
      {actions.length > 0 ? (
        <Dropdown icon label={<Icon type="context-menu" />}>
          {actions.map(({action, icon, text}, idx) => (
            <Dropdown.Option onClick={action} key={idx}>
              <Icon type={icon} /> {text}
            </Dropdown.Option>
          ))}
        </Dropdown>
      ) : (
        <div className="dropdownPlaceholder" />
      )}
    </>
  );

  return (
    <li className="ListItem">{link ? <Link to={link}>{content}</Link> : <div>{content}</div>}</li>
  );
}
