/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useCallback} from 'react';
import classnames from 'classnames';
import debounce from 'debounce';

import {Popover, Form, Switch, Button, Icon, Typeahead} from 'components';
import {VariablePreview} from 'filter';
import {t} from 'translation';
import {numberParser} from 'services';

import {getVariableValues} from './service';

import './SelectionFilter.scss';

export default function SelectionFilter({filter, type, config, setFilter, reports}) {
  const [customValues, setCustomValues] = useState([]);
  const [loadingVariableValues, setLoadingVariableValues] = useState(false);
  const [variableValues, setVariableValues] = useState(['']);

  const loadValues = useCallback(
    debounce(async (value) => {
      const reportIds = reports.map(({id}) => id).filter((id) => !!id);
      setVariableValues(await getVariableValues(reportIds, config.name, config.type, 10, value));
      setLoadingVariableValues(false);
    }, 300),
    [reports]
  );

  function isValidValue(value) {
    if (config.type !== 'String') {
      // check that its a numeric value for non string variables
      return numberParser.isFloatNumber(value);
    }
    return true;
  }

  function hasValue(value) {
    return !!filter?.values.includes(value);
  }

  function addValue(value, scopedFilter = filter) {
    const newFilter = {
      operator: config.data.operator,
      values: [...(scopedFilter?.values || []), value],
    };
    setFilter(newFilter);

    return newFilter;
  }

  function removeValue(value, scopedFilter = filter) {
    const values = scopedFilter.values.filter((existingValue) => existingValue !== value);

    const newFilter = values.length ? {operator: config.data.operator, values} : null;
    setFilter(newFilter);

    return newFilter;
  }

  const {operator, values, allowCustomValues} = config.data;

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
            {allowCustomValues && (
              <>
                {customValues.map((value, idx) => (
                  <div className="customValue" key={idx}>
                    <Switch
                      checked={hasValue(value)}
                      disabled={!isValidValue(value)}
                      onChange={({target}) => {
                        if (target.checked) {
                          addValue(value);
                        } else {
                          removeValue(value);
                        }
                      }}
                    />
                    <Typeahead
                      onOpen={() => {
                        setLoadingVariableValues(true);
                        loadValues('');
                      }}
                      onSearch={(value) => {
                        setLoadingVariableValues(true);
                        loadValues(value);
                      }}
                      onChange={(newValue) => {
                        let scopedFilter = filter;
                        if (hasValue(value)) {
                          scopedFilter = removeValue(value);
                        }
                        setCustomValues(
                          customValues.map((value, oldValueIdx) => {
                            if (oldValueIdx !== idx) return value;
                            return newValue;
                          })
                        );
                        if (isValidValue(newValue)) {
                          addValue(newValue, scopedFilter);
                        }
                      }}
                      loading={loadingVariableValues}
                      value={value}
                      typedOption
                      placeholder={t('dashboard.filter.selectValue')}
                      className={classnames({invalid: value && !isValidValue(value)})}
                    >
                      {variableValues.map((value, idx) => (
                        <Typeahead.Option key={idx} value={value}>
                          {value}
                        </Typeahead.Option>
                      ))}
                    </Typeahead>
                    <Button
                      icon
                      onClick={() => {
                        if (hasValue(value)) {
                          removeValue(value);
                        }
                        setCustomValues(
                          customValues.filter((customValue, idxToRemove) => idx !== idxToRemove)
                        );
                      }}
                    >
                      <Icon type="close-large" size="14px" />
                    </Button>
                  </div>
                ))}
                <Button
                  className="customValueAddButton"
                  onClick={() => setCustomValues([...customValues, ''])}
                >
                  <Icon type="plus" /> {t('common.value')}
                </Button>
              </>
            )}
          </fieldset>
          <hr />
          <Button className="reset-button" disabled={!filter} onClick={() => setFilter()}>
            {t('common.off')}
          </Button>
        </Form>
      </Popover>
    </div>
  );
}
