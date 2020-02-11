/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {t} from 'translation';
import {convertFilterToState} from './service';
import './DateFilterPreview.scss';

export default function DateFilterPreview({filter, filterType}) {
  const {dateType, unit, customNum, startDate, endDate} = convertFilterToState(filter);

  const highlight = text => <span className="previewItemValue">{text}</span>;

  let previewText;

  if (['today', 'yesterday'].includes(dateType)) {
    previewText = highlight(t(`common.filter.dateModal.unit.${dateType}`));
  } else if (['this', 'last'].includes(dateType)) {
    previewText = (
      <>
        {t(`common.filter.dateModal.preview.${dateType}`)}{' '}
        {dateType === 'last' && t('common.filter.dateModal.preview.completed') + ' '}
        {highlight(t(`common.unit.${makeSingular(unit)}.label`))}
      </>
    );
  } else if (dateType === 'custom') {
    const highlighted = `${+customNum} ${t(
      `common.unit.${makeSingular(unit)}.${+customNum === 1 ? 'label' : 'label-plural'}`
    )}`;
    previewText = (
      <>
        {t(`common.filter.dateModal.preview.last`)} {highlight(highlighted)}
      </>
    );
  } else if (dateType === 'fixed') {
    previewText = (
      <>
        {t('common.filter.list.operators.between')} {highlight(startDate.format('YYYY-MM-DD'))}
        {' ' + t('common.and')} {highlight(endDate.format('YYYY-MM-DD'))}
      </>
    );
  }

  return (
    <div className="DateFilterPreview">
      <span className="parameterName">{t(`common.filter.types.${filterType}`)} </span>
      {t('common.filter.list.operators.occurs')}: {previewText}
    </div>
  );
}

function makeSingular(unit) {
  return unit.slice(0, unit.length - 1);
}
