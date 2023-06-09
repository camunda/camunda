/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef, useEffect} from 'react';
import update from 'immutability-helper';

import {Table, Select, Input} from 'components';
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
              className="operator"
              value={settings.operator}
              onChange={(value) => {
                setTarget('operator', id, value);
                updateFocus(id);
              }}
            >
              <Select.Option value=">">{t('common.filter.durationModal.moreThan')}</Select.Option>
              <Select.Option value="<">{t('common.filter.durationModal.lessThan')}</Select.Option>
            </Select>
            <Input
              value={settings.value}
              type="number"
              ref={(el) => (inputsRef.current[id] = el)}
              onChange={(evt) => setTarget('value', id, evt.target.value)}
              onFocus={() => updateFocus(id)}
              onBlur={() => updateFocus(null)}
              isInvalid={!isValidInput(settings.value)}
            />
            <Select
              className="unit"
              value={settings.unit}
              onChange={(value) => {
                setTarget('unit', id, value);
                updateFocus(id);
              }}
            >
              <Select.Option value="millis">{t('common.unit.milli.label-plural')}</Select.Option>
              <Select.Option value="seconds">{t('common.unit.second.label-plural')}</Select.Option>
              <Select.Option value="minutes">{t('common.unit.minute.label-plural')}</Select.Option>
              <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
              <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
              <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
              <Select.Option value="months">{t('common.unit.month.label-plural')}</Select.Option>
              <Select.Option value="years">{t('common.unit.year.label-plural')}</Select.Option>
            </Select>
          </div>,
        ];
      })}
      disablePagination
    />
  );
}
