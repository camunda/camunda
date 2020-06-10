/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import moment from 'moment';
import classnames from 'classnames';

import {Dropdown, Icon, Popover, DatePicker, Button} from 'components';
import {t} from 'translation';

import './DateFilter.scss';

export default function DateFilter({
  filter,
  setFilter,
  icon = 'filter',
  children,
  emptyText = t('common.select'),
}) {
  const [showDatePicker, setShowDatePicker] = useState(filter?.type === 'fixed');

  function setRelativeFilter(unit, past) {
    setFilter({type: 'relative', start: {value: past ? 1 : 0, unit}, end: null});
  }

  function isFilter(unit, past) {
    return filter?.start.value === (past ? 1 : 0) && filter?.start.unit === unit;
  }

  function getFilterName(filter) {
    if (!filter) {
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
      return t('common.filter.dateModal.unit.fixed');
    }

    return (
      <>
        {moment(filter.start).format('YYYY-MM-DD')}
        <span className="to"> {t('common.filter.dateModal.to')} </span>
        {moment(filter.end).format('YYYY-MM-DD')}
      </>
    );
  }

  return (
    <div className={classnames('DateFilter__Dashboard', {fixed: filter?.type === 'fixed'})}>
      {children}
      {showDatePicker ? (
        <Popover
          title={
            <>
              <Icon type={icon} className={classnames('indicator', {active: filter})} />{' '}
              {getFixedDateFilterName(filter)}
            </>
          }
          autoOpen
        >
          <DatePicker
            forceOpen
            initialDates={{startDate: filter?.start, endDate: filter?.end}}
            onDateChange={({startDate, endDate, valid}) => {
              if (valid) {
                setFilter({
                  type: 'fixed',
                  start: startDate?.startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
                  end: endDate?.endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
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
            {t('common.reset')}
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
          <Dropdown.Option
            onClick={() => {
              setFilter();
              setShowDatePicker(true);
            }}
          >
            {t('common.filter.dateModal.unit.fixed')}
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
            {t('common.reset')}
          </Dropdown.Option>
        </Dropdown>
      )}
    </div>
  );
}
