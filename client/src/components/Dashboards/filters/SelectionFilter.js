/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Popover, Form, Switch, Button, Icon} from 'components';
import {VariablePreview} from 'filter';
import {t} from 'translation';

import './SelectionFilter.scss';

export default function SelectionFilter({filter, type, config, setFilter}) {
  function hasValue(value) {
    return !!filter?.values.includes(value);
  }

  function addValue(value) {
    setFilter({...config, values: [...(filter?.values || []), value]});
  }

  function removeValue(value) {
    const values = filter.values.filter((existingValue) => existingValue !== value);

    if (values.length) {
      setFilter({...config, values});
    } else {
      setFilter();
    }
  }

  const {operator, values} = config;

  let hintText = '';
  if (operator === 'in' || operator === 'contains') {
    hintText = t('dashboard.filter.operatorLink', {operator: t('common.filter.list.operators.or')});
  } else if (operator === 'not in' || operator === 'not contains') {
    hintText = t('dashboard.filter.operatorLink', {
      operator: t('common.filter.list.operators.nor'),
    });
  }

  let previewFilter = filter;
  if (type === 'String' && filter?.values.length > 1) {
    previewFilter = {operator: filter.operator, values: [t('dashboard.filter.multiple')]};
  }

  function getOperatorText(operator) {
    switch (operator) {
      case 'not in':
        return t('common.filter.list.operators.not');
      case '<':
        return t('common.filter.list.operators.less');
      case '>':
        return t('common.filter.list.operators.more');
      case 'contains':
        return t('common.filter.list.operators.contains');
      case 'not contains':
        return t('common.filter.list.operators.notContains');
      default:
        return t('common.filter.list.operators.is');
    }
  }

  return (
    <div className="SelectionFilter">
      <Popover
        title={
          <>
            <Icon type="filter" className={classnames('indicator', {active: filter})} />{' '}
            {filter ? (
              <VariablePreview filter={previewFilter} />
            ) : (
              getOperatorText(operator) + ' ...'
            )}
          </>
        }
      >
        <Form compact>
          <fieldset>
            <div className="hint">{hintText}</div>
            {values.map((value, idx) => (
              <Switch
                key={idx}
                label={value === null ? t('common.nullOrUndefined') : value}
                checked={hasValue(value)}
                onChange={({target}) => {
                  if (target.checked) {
                    addValue(value);
                  } else {
                    removeValue(value);
                  }
                }}
              />
            ))}
          </fieldset>
          <hr />
          <Button className="reset-button" disabled={!filter} onClick={() => setFilter()}>
            {t('common.reset')}
          </Button>
        </Form>
      </Popover>
    </div>
  );
}
