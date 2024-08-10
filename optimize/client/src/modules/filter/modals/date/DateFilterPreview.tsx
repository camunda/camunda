/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';

import {format} from 'dates';
import {t} from 'translation';
import {DateFilterType} from 'types';

import {convertFilterToState} from './service';

type VariableDateFilterPreviewProps = {
  filter: DateFilterType;
  filterType: 'variable';
  variableName: string;
};

type DateFilterPreviewProps =
  | VariableDateFilterPreviewProps
  | {
      filter: DateFilterType;
      filterType: 'variable' | 'instanceStartDate' | 'instanceEndDate' | string;
      variableName?: string;
    };

export default function DateFilterPreview({
  filter,
  filterType,
  variableName,
}: DateFilterPreviewProps) {
  const {type, unit, customNum, startDate, endDate, includeUndefined, excludeUndefined} =
    convertFilterToState(filter);

  const bolden = (text: string | JSX.Element[]) => <b>{text}</b>;

  let previewText;

  if (type === 'today' || type === 'yesterday') {
    previewText = bolden(t(`common.filter.dateModal.unit.${type}`));
  } else if ((type === 'this' || type === 'last') && unit) {
    const translationType = unit === 'weeks' ? 'week' : 'all';
    previewText = (
      <>
        {t(`common.filter.dateModal.preview.${type}.${translationType}`)}{' '}
        {type === 'last' && t(`common.filter.dateModal.preview.completed.${translationType}`) + ' '}
        {bolden(t(`common.unit.${makeSingular(unit)}.label`))}
      </>
    );
  } else if (type === 'custom' && customNum && unit) {
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
  } else if (type === 'between' && startDate && endDate) {
    const dateFormat = getFixedDateFormat(startDate, endDate);

    previewText = (
      <>
        {t('common.filter.list.operators.between')} {bolden(format(startDate, dateFormat))}
        {' ' + t('common.and')} {bolden(format(endDate, dateFormat))}
      </>
    );
  } else if (type === 'after' && startDate && endDate !== undefined) {
    previewText = (
      <>
        {t('common.filter.list.operators.after')}{' '}
        {bolden(format(startDate, getFixedDateFormat(startDate, endDate)))}
      </>
    );
  } else if (type === 'before' && endDate && startDate !== undefined) {
    previewText = (
      <>
        {t('common.filter.list.operators.before')}{' '}
        {bolden(format(endDate, getFixedDateFormat(startDate, endDate)))}
      </>
    );
  }

  if (filterType === 'variable') {
    const createOperator = (operator: ReturnType<typeof t>) => <span> {operator} </span>;
    const operator = createOperator(
      excludeUndefined
        ? t('common.filter.list.operators.nor')
        : t('common.filter.list.operators.or')
    );

    return (
      <div className="DateFilterPreview">
        <Tag type="blue" className="parameterName">
          {variableName}
        </Tag>
        <span className="filterText">
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
        </span>
      </div>
    );
  } else {
    return (
      <div className="DateFilterPreview">
        <Tag type="blue" className="parameterName">
          {t(`common.filter.types.${filterType}`)}{' '}
        </Tag>
        <span className="filterText">
          {t('common.filter.list.operators.occurs')} {previewText}
        </span>
      </div>
    );
  }
}

function makeSingular(unit: string): string {
  return unit.slice(0, unit.length - 1);
}

function getFixedDateFormat(startDate: Date | null, endDate: Date | null): string {
  const containsTime =
    (startDate && format(startDate, 'HH:mm:ss') !== '00:00:00') ||
    (endDate && format(endDate, 'HH:mm:ss') !== '23:59:59');

  return containsTime ? `yyyy-MM-dd '${t('common.timeAt')}' HH:mm` : 'yyy-MM-dd';
}
