/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Link} from 'react-router-dom';
import {ChevronDown} from '@carbon/icons-react';
import {t} from 'translation';

import {Popover, Tooltip} from 'components';

import './EntityName.scss';

export default function EntityName({children, details, linkTo, onClick}) {
  return (
    <div className="EntityName">
      <div className="name-container">
        <Tooltip content={children} overflowOnly position="bottom" theme="dark" delay={0}>
          {linkTo ? (
            <Link to={linkTo} onClick={onClick} className="name">
              {children}
            </Link>
          ) : (
            <h1 className="name">{children}</h1>
          )}
        </Tooltip>
        {details && (
          <Popover
            trigger={
              <Popover.Button
                size="sm"
                iconDescription={t('common.details')}
                hasIconOnly
                renderIcon={ChevronDown}
                className="DetailsPopoverButton"
              />
            }
            floating
          >
            {details}
          </Popover>
        )}
      </div>
    </div>
  );
}
