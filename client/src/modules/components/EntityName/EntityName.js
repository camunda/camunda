/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import {CarbonPopover, Tooltip, gridEntryClassName} from 'components';

import './EntityName.scss';

export default function EntityName({children, details, linkTo}) {
  return (
    <div className="EntityName">
      <div className="name-container">
        <Tooltip content={children} overflowOnly position="bottom" theme="dark" delay={0}>
          {linkTo ? (
            <Link to={linkTo} className="name">
              {children}
            </Link>
          ) : (
            <h1 className="name">{children}</h1>
          )}
        </Tooltip>
        {details && (
          <CarbonPopover icon="down" floating alignContainer={'.' + gridEntryClassName}>
            {details}
          </CarbonPopover>
        )}
      </div>
    </div>
  );
}
