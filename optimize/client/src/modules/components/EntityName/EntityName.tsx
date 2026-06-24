/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MouseEventHandler, ReactNode} from 'react';
import {Link} from 'react-router-dom';
import {ChevronDown} from '@carbon/icons-react';

import {t} from 'translation';

import {Popover} from 'components';

import './EntityName.scss';

interface EntityNameProps {
  name: string;
  details?: ReactNode;
  linkTo?: string;
  onClick?: MouseEventHandler<HTMLAnchorElement>;
}

export default function EntityName({name, details, linkTo, onClick}: EntityNameProps) {
  return (
    <div className="EntityName">
      <div className="name-container">
        {linkTo ? (
          <Link to={linkTo} onClick={onClick} className="cds--link name" title={name}>
            {name}
          </Link>
        ) : (
          <h1 className="name" title={name}>
            {name}
          </h1>
        )}
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
