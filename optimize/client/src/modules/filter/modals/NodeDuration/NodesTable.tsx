/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useEffect} from 'react';
import update from 'immutability-helper';
import {TextInput} from '@carbon/react';

import {Table, Select} from 'components';
import {t} from 'translation';
import {FilterData} from 'types';

import {isValidInput} from './service';

import './NodesTable.scss';

const defaultValue = {operator: '>', value: '', unit: 'hours'};

interface NodesTableProps {
  focus?: string | null;
  updateFocus: (id: string | null) => void;
  values: Record<string, FilterData>;
  nodeNames: Record<string, string>;
  onChange: (value: NodesTableProps['values']) => void;
}

export default function NodesTable({
  focus,
  updateFocus,
  values,
  nodeNames,
  onChange,
}: NodesTableProps) {
  const inputsRef = useRef<Record<string, HTMLElement | null>>({});

  useEffect(() => {
    if (focus) {
      inputsRef.current[focus]?.focus();
    }
  }, [focus]);

  function setTarget(type: string, id: string, value: string) {
    if (values[id]) {
      onChange(update(values, {[id]: {[type]: {$set: value}}}));
    } else {
      onChange(
        update(values, {
          [id]: {
            $set: {
              ...defaultValue,
              [type]: value,
            },
          },
        })
      );
    }
  }

  return (
    <Table
      className="NodesTable"
      head={[
        t('report.heatTarget.table.activity').toString(),
        t('common.filter.types.duration').toString(),
      ]}
      body={Object.keys(values).map<(string | JSX.Element)[]>((id) => {
        const settings = values[id] || defaultValue;
        const nodeName = nodeNames[id]!;
        return [
          nodeName,
          <div className="selection">
            <Select
              size="sm"
              id={`flow-node-${id}-more-less-selector`}
              className="operator"
              value={settings.operator}
              onChange={(value) => {
                setTarget('operator', id, value);
                updateFocus(id);
              }}
            >
              <Select.Option value=">" label={t('common.filter.durationModal.moreThan')} />
              <Select.Option value="<" label={t('common.filter.durationModal.lessThan')} />
            </Select>
            <TextInput
              size="sm"
              id={`flow-node-${id}-duration-value-input`}
              labelText={t('common.value')}
              hideLabel
              value={settings.value}
              type="number"
              ref={(el: HTMLInputElement) => (inputsRef.current[id] = el)}
              onChange={(evt) => setTarget('value', id, evt.target.value)}
              onFocus={() => updateFocus(id)}
              onBlur={() => updateFocus(null)}
              invalid={!isValidInput(settings.value)}
            />
            <Select
              size="sm"
              id={`flow-node-${id}-unit-selector`}
              className="unit"
              value={settings.unit}
              onChange={(value) => {
                setTarget('unit', id, value);
                updateFocus(id);
              }}
            >
              <Select.Option value="millis" label={t('common.unit.milli.label-plural')} />
              <Select.Option value="seconds" label={t('common.unit.second.label-plural')} />
              <Select.Option value="minutes" label={t('common.unit.minute.label-plural')} />
              <Select.Option value="hours" label={t('common.unit.hour.label-plural')} />
              <Select.Option value="days" label={t('common.unit.day.label-plural')} />
              <Select.Option value="weeks" label={t('common.unit.week.label-plural')} />
              <Select.Option value="months" label={t('common.unit.month.label-plural')} />
              <Select.Option value="years" label={t('common.unit.year.label-plural')} />
            </Select>
          </div>,
        ];
      })}
      disablePagination
    />
  );
}
