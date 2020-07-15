/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';
import classnames from 'classnames';

import {Button, Input, LabeledInput} from 'components';
import {t} from 'translation';

import './ValueListInput.scss';

export default function ValueListInput({
  filter,
  allowUndefined,
  allowMultiple,
  onChange,
  className,
}) {
  const {includeUndefined, values} = filter;

  return (
    <div className={classnames('ValueListInput', className)}>
      <ul>
        {allowUndefined && (
          <LabeledInput
            className="undefinedOption"
            type="checkbox"
            checked={includeUndefined}
            label={t('common.nullOrUndefined')}
            onChange={({target: {checked}}) =>
              onChange(update(filter, {includeUndefined: {$set: checked}}))
            }
          />
        )}
        {(values || []).map((value, idx) => {
          return (
            <li key={idx} className="valueListItem">
              <Input
                type="text"
                value={value}
                data-idx={idx}
                onChange={({target}) => {
                  const newValues = [...values];
                  newValues[target.getAttribute('data-idx')] = target.value;

                  onChange({...filter, values: newValues});
                }}
                placeholder={t('common.filter.variableModal.enterValue')}
              />
              {values.length > 1 && (
                <Button
                  onClick={(evt) => {
                    evt.preventDefault();
                    onChange({
                      ...filter,
                      values: values.filter((_, index) => idx !== index),
                    });
                  }}
                  className="removeItemButton"
                >
                  ×
                </Button>
              )}
            </li>
          );
        })}
        {allowMultiple && (
          <li className="valueListButton">
            <Button
              onClick={() => onChange(update(filter, {values: {$push: ['']}}))}
              className="addValueButton"
            >
              {t('common.filter.variableModal.addValue')}
            </Button>
          </li>
        )}
      </ul>
    </div>
  );
}
