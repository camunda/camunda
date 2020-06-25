/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

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
            {moment(lastModified).format('lll')}
            <br />
            {t('common.entity.byModifier', {modifier: lastModifier})}
          </dd>
        </>
      )}
    </dl>
  );
}
