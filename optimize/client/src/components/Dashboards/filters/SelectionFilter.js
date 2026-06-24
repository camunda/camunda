/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Button, ComboBox, Form, FormGroup, Stack, Toggle} from '@carbon/react';
import {Add, Filter, TrashCan} from '@carbon/icons-react';
import classnames from 'classnames';

import {Popover} from 'components';
import {VariablePreview} from 'filter';
import {t} from 'translation';
import {numberParser} from 'services';
import debouncePromise from 'debouncePromise';

import {getVariableValues} from './service';

import './SelectionFilter.scss';

const debounceRequest = debouncePromise();

export default function SelectionFilter({filter, type, config, setFilter, reports, resetTrigger}) {
  const {
    defaultValues,
    data: {operator, values, allowCustomValues},
  } = config;

  const [customValues, setCustomValues] = useState([]);
  const [variableValues, setVariableValues] = useState(['']);
  const [searchedValue, setSearchedValue] = useState('');

  useEffect(() => {
    setCustomValues((defaultValues ?? []).filter((value) => value && !values.includes(value)));
  }, [defaultValues, values, resetTrigger]);

  const loadValues = async (value) => {
    const values = await debounceRequest(async () => {
      const reportIds = reports.map(({id}) => id).filter((id) => !!id);
      return await getVariableValues(reportIds, config.name, config.type, 10, value);
    }, 300);

    setVariableValues(values);
  };

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

  const toggleValue = (value) => (checked) => {
    if (checked) {
      addValue(value);
    } else {
      removeValue(value);
    }
  };

  const variableItems = [searchedValue, ...variableValues].filter(Boolean);

  return (
    <div className="SelectionFilter">
      <Popover
        isTabTip
        trigger={
          <Popover.ListBox size="sm">
            <Filter className={classnames('indicator', {active: filter})} />
            {filter ? (
              <VariablePreview filter={previewFilter} />
            ) : (
              getOperatorText(operator) + ' ...'
            )}
          </Popover.ListBox>
        }
      >
        <Form>
          <FormGroup legendText={hintText}>
            <Stack gap={4}>
              {values.map((value, idx) => (
                <Toggle
                  id={`${idx}`}
                  size="sm"
                  key={idx}
                  hideLabel
                  labelText={value === null ? t('common.nullOrUndefined') : value}
                  toggled={hasValue(value)}
                  onToggle={toggleValue(value)}
                />
              ))}
              {allowCustomValues && (
                <>
                  {customValues.map((value, idx) => (
                    <div className="customValue" key={idx}>
                      <Toggle
                        id={`${idx}`}
                        size="sm"
                        hideLabel
                        labelText={t('dashboard.filter.customValue')}
                        toggled={hasValue(value)}
                        disabled={!isValidValue(value)}
                        onToggle={toggleValue(value)}
                      />
                      <ComboBox
                        id={`valueSelection-${idx}`}
                        className="valueSelection"
                        size="sm"
                        items={variableItems}
                        onChange={({selectedItem}) => {
                          let scopedFilter = filter;
                          if (hasValue(value)) {
                            scopedFilter = removeValue(value);
                          }
                          setCustomValues(
                            customValues.map((value, oldValueIdx) => {
                              if (oldValueIdx !== idx) {
                                return value;
                              }
                              return selectedItem;
                            })
                          );
                          if (isValidValue(selectedItem)) {
                            addValue(selectedItem, scopedFilter);
                          }
                        }}
                        onInputChange={(value) => {
                          setSearchedValue(value);
                          loadValues(value);
                        }}
                        placeholder={t('dashboard.filter.selectValue')}
                        selectedItem={value}
                        invalid={!!value && !isValidValue(value)}
                      />
                      <Button
                        kind="ghost"
                        size="sm"
                        hasIconOnly
                        renderIcon={TrashCan}
                        iconDescription={t('common.remove')}
                        onClick={() => {
                          if (hasValue(value)) {
                            removeValue(value);
                          }
                          setCustomValues(
                            customValues.filter((_customValue, idxToRemove) => idx !== idxToRemove)
                          );
                        }}
                      />
                    </div>
                  ))}
                  <Button
                    size="sm"
                    kind="tertiary"
                    className="customValueAddButton"
                    onClick={() => setCustomValues([...customValues, ''])}
                    renderIcon={Add}
                  >
                    {t('common.value')}
                  </Button>
                </>
              )}
            </Stack>
          </FormGroup>
          <hr />
          <Button
            size="sm"
            kind="ghost"
            className="reset-button"
            disabled={!filter}
            onClick={() => setFilter()}
          >
            {t('common.off')}
          </Button>
        </Form>
      </Popover>
    </div>
  );
}
