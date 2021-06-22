/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {format} from 'dates';
import {t} from 'translation';

import {convertFilterToState} from './service';

import './DateFilterPreview.scss';

export default function DateFilterPreview({filter, filterType, variableName}) {
  const {type, unit, customNum, startDate, endDate, includeUndefined, excludeUndefined} =
    convertFilterToState(filter);

  const bolden = (text) => <b>{text}</b>;

  let previewText;

  if (['today', 'yesterday'].includes(type)) {
    previewText = bolden(t(`common.filter.dateModal.unit.${type}`));
  } else if (['this', 'last'].includes(type)) {
    const translationType = unit === 'weeks' ? 'week' : 'all';
    previewText = (
      <>
        {t(`common.filter.dateModal.preview.${type}.${translationType}`)}{' '}
        {type === 'last' && t(`common.filter.dateModal.preview.completed.${translationType}`) + ' '}
        {bolden(t(`common.unit.${makeSingular(unit)}.label`))}
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
        {t('common.filter.dateModal.preview.last.' + prefix)} {bolden(highlighted)}
      </>
    );
  } else if (type === 'fixed') {
    const containsTime =
      format(startDate, 'HH:mm:ss') !== '00:00:00' || format(endDate, 'HH:mm:ss') !== '23:59:59';

    const dateFormat = containsTime ? `yyyy-MM-dd '${t('common.timeAt')}' HH:mm` : 'yyy-MM-dd';

    previewText = (
      <>
        {t('common.filter.list.operators.between')} {bolden(format(startDate, dateFormat))}
        {' ' + t('common.and')} {bolden(format(endDate, dateFormat))}
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
            {bolden(t('common.null'))}
            {operator}
            {bolden(t('common.undefined'))}
          </>
        )}
      </div>
    );
  } else {
    return (
      <div className="DateFilterPreview">
        <span className="parameterName">{t(`common.filter.types.${filterType}`)} </span>
        <span className="filterText">
          {t('common.filter.list.operators.occurs')} {previewText}
        </span>
      </div>
    );
  }
}

function makeSingular(unit) {
  return unit.slice(0, unit.length - 1);
}
