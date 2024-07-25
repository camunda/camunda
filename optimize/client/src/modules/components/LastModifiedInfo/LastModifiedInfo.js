/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import {t} from 'translation';

export default function LastModifiedInfo({entity}) {
  const {lastModified, lastModifier, owner} = entity;

  return (
    <dl className="LastModifiedInfo">
      {owner && (
        <>
          <dt>{t('common.entity.createdBy')}</dt>
          <dd>{owner}</dd>
        </>
      )}

      {lastModified && lastModifier && (
        <>
          <dt>{t('common.entity.modifiedTitle')}</dt>
          <dd>
            {format(parseISO(lastModified), 'PPp')}
            <br />
            {t('common.entity.byModifier', {modifier: lastModifier})}
          </dd>
        </>
      )}
    </dl>
  );
}
