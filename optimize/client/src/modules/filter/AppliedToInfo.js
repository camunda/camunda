/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip} from '@carbon/react';

import {t} from 'translation';

import './AppliedToInfo.scss';

export default function AppliedToInfo({filter, definitions}) {
  const {appliedTo = []} = filter;
  const appliedToAll = appliedTo?.[0] === 'all';

  const selectedDefinitions = appliedToAll
    ? definitions
    : definitions.filter((def) => appliedTo.includes(def.identifier));

  if (!definitions || definitions.length <= 1 || !appliedTo) {
    return null;
  }

  let innerText = `${t('common.filter.list.appliedTo')}: ${appliedTo.length} ${t(
    'common.process.label' + (appliedTo.length > 1 ? '-plural' : '')
  )}`;

  if (appliedToAll) {
    innerText = `${t('common.filter.list.appliedTo')}: ${t('common.all').toLowerCase()} ${t(
      'common.process.label-plural'
    )}`;
  }

  return (
    <Tooltip
      label={
        <div className="appliesTo">
          {t('common.filter.list.appliedTo')}:
          <ul>
            {selectedDefinitions.map(({key, displayName}) => (
              <li key={key}>{displayName}</li>
            ))}
          </ul>
        </div>
      }
      align="bottom"
      autoAlign
    >
      <p className="appliedTo">{innerText}</p>
    </Tooltip>
  );
}
