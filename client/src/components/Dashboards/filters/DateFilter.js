/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';
import {parseISO, startOfDay, endOfDay} from 'date-fns';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {Dropdown, Icon, CarbonPopover, DatePicker, Button} from 'components';
import {t} from 'translation';

import RollingFilter from './RollingFilter';

import './DateFilter.scss';

export default function DateFilter({
  filter,
  setFilter,
  icon = 'filter',
  children,
  emptyText = t('common.select'),
  title,
  simplified,
}) {
  const isFixed = filter?.type === 'fixed' || filter?.type === 'rolling';

  const [showFixedPopover, setShowFixedPopover] = useState(isFixed);
  const [autoOpen, setAutoOpen] = useState(false);
  const startDate = filter?.type === 'fixed' && filter?.start ? parseISO(filter.start) : null;
  const endDate = filter?.type === 'fixed' && filter?.end ? parseISO(filter.end) : null;

  const [fixedType, setFixedType] = useState(getFixedType(startDate, endDate));

  useEffect(() => {
    setShowFixedPopover(isFixed);
    setAutoOpen(false);
  }, [isFixed]);

  useEffect(() => {
    setFixedType(getFixedType(startDate, endDate));
  }, [endDate, filter, startDate]);

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
    if (!filter || filter?.type === 'fixed' || filter?.type === 'rolling') {
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
      if (fixedType === 'rolling') {
        return emptyText;
      }

      return t('common.filter.dateModal.unit.' + fixedType);
    }

    if (fixedType === 'rolling') {
      const value = filter.start.value;
      const unit = filter.start.unit.slice(0, -1);

      let prefix = 'all';
      if (+value > 1) {
        prefix = 'plural';
      } else if (['weeks', 'minutes', 'hours'].includes(unit)) {
        prefix = 'week';
      }
      return (
        <>
          {t('common.filter.list.operators.occurs')}{' '}
          {t('common.filter.dateModal.preview.last.' + prefix)}{' '}
          {`${+value} ${t(`common.unit.${unit}.${+value === 1 ? 'label' : 'label-plural'}`)}`}
        </>
      );
    }

    return (
      <>
        {t('common.filter.dateModal.unit.' + fixedType)}{' '}
        {fixedType !== 'before' && startDate && format(startDate, 'yyyy-MM-dd')}
        {fixedType === 'between' && <span className="to"> {t('common.filter.dateModal.to')} </span>}
        {fixedType !== 'after' && endDate && format(endDate, 'yyyy-MM-dd')}
      </>
    );
  }

  function openDatePicker(type) {
    setFilter();
    setFixedType(type);
    setShowFixedPopover(true);
    setAutoOpen(true);
  }

  function openRollingFilter() {
    setFilter({
      type: 'rolling',
      start: {value: 2, unit: 'days'},
      end: null,
      excludeUndefined: false,
      includeUndefined: false,
    });
    setFixedType('rolling');
    setShowFixedPopover(true);
    setAutoOpen(true);
  }

  function updateRolling({value, unit}) {
    setFilter({
      type: 'rolling',
      start: {value: value || filter?.start?.value, unit: unit || filter?.start?.unit},
      end: null,
      excludeUndefined: false,
      includeUndefined: false,
    });
  }

  return (
    <div className={classnames('DateFilter__Dashboard', {fixed: filter?.type === 'fixed'})}>
      <div className="title">
        {title}
        {children}
      </div>
      {showFixedPopover && filter?.type !== 'relative' ? (
        <CarbonPopover
          title={
            <>
              <Icon type={icon} className={classnames('indicator', {active: filter})} />{' '}
              {getFixedDateFilterName(filter)}
            </>
          }
          autoOpen={autoOpen}
        >
          {fixedType === 'rolling' ? (
            <RollingFilter filter={filter} onChange={updateRolling} />
          ) : (
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
          )}
          <hr />
          <Button
            className="reset-button"
            onClick={() => {
              setFilter();
              setShowFixedPopover(false);
            }}
          >
            {t('common.off')}
          </Button>
        </CarbonPopover>
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
          {!simplified && (
            <>
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
            </>
          )}
          <Dropdown.Option onClick={() => openRollingFilter()}>
            {t('common.filter.dateModal.unit.custom')}
          </Dropdown.Option>
          {!simplified && (
            <>
              <hr />
              <Dropdown.Option disabled={!filter} onClick={() => setFilter()}>
                {t('common.off')}
              </Dropdown.Option>
            </>
          )}
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
  } else if (end) {
    return 'before';
  } else {
    return 'rolling';
  }
};
