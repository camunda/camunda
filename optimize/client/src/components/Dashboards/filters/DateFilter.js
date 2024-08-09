/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';
import {parseISO, startOfDay, endOfDay} from 'date-fns';
import {Button, MenuItem, MenuItemSelectable, MenuItemDivider} from '@carbon/react';
import {Filter} from '@carbon/icons-react';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {Popover, DatePicker} from 'components';
import {t} from 'translation';

import RollingFilter from './RollingFilter';

import './DateFilter.scss';

export default function DateFilter({
  filter,
  setFilter,
  icon: Icon = Filter,
  children,
  emptyText = t('common.select'),
  title,
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
        {fixedType === 'between' && <span className="to">{t('common.filter.dateModal.to')}</span>}
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
        <Popover
          isTabTip
          trigger={
            <Popover.ListBox size="sm">
              <Icon className={classnames('indicator', {active: filter})} />
              {getFixedDateFilterName(filter)}
            </Popover.ListBox>
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
            size="sm"
            kind="ghost"
            className="reset-button"
            onClick={() => {
              setFilter();
              setShowFixedPopover(false);
            }}
          >
            {t('common.off')}
          </Button>
        </Popover>
      ) : (
        <MenuDropdown
          size="sm"
          label={
            <>
              <Icon className={classnames('indicator', {active: filter})} />
              {getFilterName(filter)}
            </>
          }
          className="filterMenu"
          menuTarget={document.querySelector('.fullscreen')}
        >
          <MenuItem
            label={t('common.filter.dateModal.unit.between')}
            onClick={() => openDatePicker('between')}
          />
          <MenuItem
            label={t('common.filter.dateModal.unit.after')}
            onClick={() => openDatePicker('after')}
          />
          <MenuItem
            label={t('common.filter.dateModal.unit.before')}
            onClick={() => openDatePicker('before')}
          />
          <MenuItemSelectable
            label={t('common.filter.dateModal.unit.today')}
            selected={isFilter('days')}
            onChange={() => setRelativeFilter('days')}
          />
          <MenuItemSelectable
            label={t('common.filter.dateModal.unit.yesterday')}
            selected={isFilter('days', true)}
            onChange={() => setRelativeFilter('days', true)}
          />
          <MenuItemSelectable
            label={t('dashboard.filter.date.last')}
            selected={filter?.start.value === 1 && !isFilter('days', true)}
          >
            {['weeks', 'months', 'years', 'quarters'].map((unit) => (
              <MenuItemSelectable
                key={unit}
                label={t('dashboard.filter.date.units.' + unit)}
                selected={isFilter(unit, true)}
                onChange={() => setRelativeFilter(unit, true)}
              />
            ))}
          </MenuItemSelectable>
          <MenuItemSelectable
            label={t('dashboard.filter.date.this')}
            selected={filter?.start.value === 0 && !isFilter('days')}
          >
            {['weeks', 'months', 'years', 'quarters'].map((unit) => (
              <MenuItemSelectable
                key={unit}
                label={t('dashboard.filter.date.units.' + unit)}
                selected={isFilter(unit)}
                onChange={() => setRelativeFilter(unit)}
              />
            ))}
          </MenuItemSelectable>
          <MenuItem
            label={t('common.filter.dateModal.unit.custom')}
            onClick={() => openRollingFilter()}
          />
          <MenuItemDivider />
          <MenuItem disabled={!filter} label={t('common.off')} onClick={() => setFilter()} />
        </MenuDropdown>
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
