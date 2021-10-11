/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';
import {parseISO, startOfDay, endOfDay} from 'date-fns';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {Dropdown, Icon, Popover, DatePicker, Button} from 'components';
import {t} from 'translation';

import './DateFilter.scss';

export default function DateFilter({
  filter,
  setFilter,
  icon = 'filter',
  children,
  emptyText = t('common.select'),
  title,
}) {
  const isFixed = filter?.type === 'fixed';

  const [showDatePicker, setShowDatePicker] = useState(isFixed);
  const [autoOpen, setAutoOpen] = useState(false);
  const startDate = filter?.start ? parseISO(filter.start) : null;
  const endDate = filter?.end ? parseISO(filter.end) : null;

  const [fixedType, setFixedType] = useState(getFixedType(startDate, endDate));

  useEffect(() => {
    setShowDatePicker(isFixed);
    setAutoOpen(false);
  }, [isFixed]);

  function setRelativeFilter(unit, past) {
    setFilter({
      type: 'relative',
      start: {value: past ? 1 : 0, unit},
      end: null,
      excludeUndefined: false,
      includeUndefined: false,
    });
  }

  function isFilter(unit, past) {
    return filter?.start.value === (past ? 1 : 0) && filter?.start.unit === unit;
  }

  function getFilterName(filter) {
    if (!filter || filter?.type === 'fixed') {
      return emptyText;
    }

    if (isFilter('days')) {
      return t('common.filter.dateModal.unit.today');
    }

    if (isFilter('days', true)) {
      return t('common.filter.dateModal.unit.yesterday');
    }

    return (
      t(`dashboard.filter.date.${filter.start.value ? 'last' : 'this'}`) +
      ' ' +
      t('dashboard.filter.date.units.' + filter.start.unit)
    );
  }

  function getFixedDateFilterName(filter) {
    if (!filter) {
      return t('common.filter.dateModal.unit.' + fixedType);
    }

    return (
      <>
        {t('common.filter.dateModal.unit.' + fixedType)}{' '}
        {fixedType !== 'before' && format(startDate, 'yyyy-MM-dd')}
        {fixedType === 'between' && <span className="to"> {t('common.filter.dateModal.to')} </span>}
        {fixedType !== 'after' && format(endDate, 'yyyy-MM-dd')}
      </>
    );
  }

  function openDatePicker(type) {
    setFilter();
    setFixedType(type);
    setShowDatePicker(true);
    setAutoOpen(true);
  }

  return (
    <div className={classnames('DateFilter__Dashboard', {fixed: filter?.type === 'fixed'})}>
      <div className="title">
        {title}
        {children}
      </div>
      {showDatePicker && filter?.type !== 'relative' ? (
        <Popover
          title={
            <>
              <Icon type={icon} className={classnames('indicator', {active: filter})} />{' '}
              {getFixedDateFilterName(filter)}
            </>
          }
          autoOpen={autoOpen}
        >
          <DatePicker
            type={fixedType}
            forceOpen
            initialDates={{startDate, endDate}}
            onDateChange={({startDate, endDate, valid}) => {
              if (valid) {
                setFilter({
                  type: 'fixed',
                  start: startDate ? format(startOfDay(startDate), BACKEND_DATE_FORMAT) : null,
                  end: endDate ? format(endOfDay(endDate), BACKEND_DATE_FORMAT) : null,
                  excludeUndefined: false,
                  includeUndefined: false,
                });
              }
            }}
          />
          <hr />
          <Button
            className="reset-button"
            onClick={() => {
              setFilter();
              setShowDatePicker(false);
            }}
          >
            {t('common.off')}
          </Button>
        </Popover>
      ) : (
        <Dropdown
          label={
            <>
              <Icon type={icon} className={classnames('indicator', {active: filter})} />
              {getFilterName(filter)}
            </>
          }
        >
          <Dropdown.Option onClick={() => openDatePicker('between')}>
            {t('common.filter.dateModal.unit.between')}
          </Dropdown.Option>
          <Dropdown.Option onClick={() => openDatePicker('after')}>
            {t('common.filter.dateModal.unit.after')}
          </Dropdown.Option>
          <Dropdown.Option onClick={() => openDatePicker('before')}>
            {t('common.filter.dateModal.unit.before')}
          </Dropdown.Option>
          <Dropdown.Option checked={isFilter('days')} onClick={() => setRelativeFilter('days')}>
            {t('common.filter.dateModal.unit.today')}
          </Dropdown.Option>
          <Dropdown.Option
            checked={isFilter('days', true)}
            onClick={() => setRelativeFilter('days', true)}
          >
            {t('common.filter.dateModal.unit.yesterday')}
          </Dropdown.Option>
          <Dropdown.Submenu
            label={t('dashboard.filter.date.last')}
            checked={filter?.start.value === 1 && !isFilter('days', true)}
          >
            {['weeks', 'months', 'years', 'quarters'].map((unit) => (
              <Dropdown.Option
                key={unit}
                checked={isFilter(unit, true)}
                onClick={() => setRelativeFilter(unit, true)}
              >
                {t('dashboard.filter.date.units.' + unit)}
              </Dropdown.Option>
            ))}
          </Dropdown.Submenu>
          <Dropdown.Submenu
            label={t('dashboard.filter.date.this')}
            checked={filter?.start.value === 0 && !isFilter('days')}
          >
            {['weeks', 'months', 'years', 'quarters'].map((unit) => (
              <Dropdown.Option
                key={unit}
                checked={isFilter(unit)}
                onClick={() => setRelativeFilter(unit)}
              >
                {t('dashboard.filter.date.units.' + unit)}
              </Dropdown.Option>
            ))}
          </Dropdown.Submenu>
          <hr />
          <Dropdown.Option disabled={!filter} onClick={() => setFilter()}>
            {t('common.off')}
          </Dropdown.Option>
        </Dropdown>
      )}
    </div>
  );
}

const getFixedType = (start, end) => {
  if (start && end) {
    return 'between';
  } else if (start) {
    return 'after';
  } else {
    return 'before';
  }
};
