/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {t} from 'translation';
import {convertFilterToState} from './service';
import './DateFilterPreview.scss';

export default function DateFilterPreview({filter, filterType, variableName}) {
  const {
    type,
    unit,
    customNum,
    startDate,
    endDate,
    includeUndefined,
    excludeUndefined,
  } = convertFilterToState(filter);

  const highlight = (text) => <span className="previewItemValue">{text}</span>;

  let previewText;

  if (['today', 'yesterday'].includes(type)) {
    previewText = highlight(t(`common.filter.dateModal.unit.${type}`));
  } else if (['this', 'last'].includes(type)) {
    const translationType = unit === 'weeks' ? 'week' : 'all';
    previewText = (
      <>
        {t(`common.filter.dateModal.preview.${type}.${translationType}`)}{' '}
        {type === 'last' && t(`common.filter.dateModal.preview.completed.${translationType}`) + ' '}
        {highlight(t(`common.unit.${makeSingular(unit)}.label`))}
      </>
    );
  } else if (type === 'custom') {
    const highlighted = `${+customNum} ${t(
      `common.unit.${makeSingular(unit)}.${+customNum === 1 ? 'label' : 'label-plural'}`
    )}`;

    let prefix = 'all';
    if (+customNum > 1) {
      prefix = 'plural';
    } else if (['weeks', 'minutes', 'hours'].includes(unit)) {
      prefix = 'week';
    }
    previewText = (
      <>
        {t('common.filter.dateModal.preview.last.' + prefix)} {highlight(highlighted)}
      </>
    );
  } else if (type === 'fixed') {
    previewText = (
      <>
        {t('common.filter.list.operators.between')} {highlight(startDate.format('YYYY-MM-DD'))}
        {' ' + t('common.and')} {highlight(endDate.format('YYYY-MM-DD'))}
      </>
    );
  }

  if (filterType === 'variable') {
    const createOperator = (operator) => <span> {operator} </span>;
    const operator = createOperator(
      excludeUndefined
        ? t('common.filter.list.operators.nor')
        : t('common.filter.list.operators.or')
    );

    return (
      <div className="DateFilterPreview">
        <span className="parameterName">{variableName}</span>
        {createOperator(
          excludeUndefined
            ? t('common.filter.list.operators.not')
            : t('common.filter.list.operators.is')
        )}
        {previewText}
        {(excludeUndefined || includeUndefined) && (
          <>
            {previewText && operator}
            {highlight(t('common.null'))}
            {operator}
            {highlight(t('common.undefined'))}
          </>
        )}
      </div>
    );
  } else {
    return (
      <div className="DateFilterPreview">
        <span className="parameterName">{t(`common.filter.types.${filterType}`)} </span>
        {t('common.filter.list.operators.occurs')}: {previewText}
      </div>
    );
  }
}

function makeSingular(unit) {
  return unit.slice(0, unit.length - 1);
}
