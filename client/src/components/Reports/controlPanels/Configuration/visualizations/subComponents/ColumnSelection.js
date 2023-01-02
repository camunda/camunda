/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {LabeledInput} from 'components';
import {t} from 'translation';
import {getReportResult} from 'services';
import {getVariableLabel} from 'variables';

import AllColumnsButtons from './AllColumnsButtons';
import CollapsibleSection from './CollapsibleSection';
import ColumnSwitch from './ColumnSwitch';

import './ColumnSelection.scss';

const labels = {
  inputVariables: 'input',
  outputVariables: 'output',
  variables: 'variable',
  flowNodeDurations: 'flowNodeDuration',
};

export default function ColumnSelection({report, onChange}) {
  const {data} = report;
  const [isSectionOpen, setIsSectionOpen] = useState(
    Object.keys(labels).reduce((prev, key) => ({...prev, [key]: false}), {})
  );

  const reportColumns = getReportResult(report)?.data[0];

  if (!reportColumns) {
    return null;
  }

  const toggleSectionOpen = (sectionKey) =>
    setIsSectionOpen((prev) => ({...prev, [sectionKey]: !prev[sectionKey]}));

  const {
    tableColumns: {excludedColumns, includedColumns, includeNewVariables},
  } = data.configuration;

  const groupedColumns = groupColumns(reportColumns);

  const columnNames = getColumnNames(reportColumns);

  return (
    <fieldset className="ColumnSelection">
      <legend>{t('report.config.includeTableColumn')}</legend>
      <AllColumnsButtons
        enableAll={() =>
          onChange({
            tableColumns: {excludedColumns: {$set: []}, includedColumns: {$set: columnNames}},
          })
        }
        disableAll={() =>
          onChange({
            tableColumns: {excludedColumns: {$set: columnNames}, includedColumns: {$set: []}},
          })
        }
      />
      <LabeledInput
        className="includeNew"
        type="checkbox"
        checked={includeNewVariables}
        label={t('report.config.includeNewVariables')}
        onChange={({target: {checked}}) =>
          onChange({tableColumns: {includeNewVariables: {$set: checked}}})
        }
      />
      {groupedColumns.map(({key, value}) => {
        const isSection = typeof value === 'object' && value !== null;

        if (isSection && Object.keys(value).length === 0) {
          return null;
        }

        if (isSection) {
          let sectionType = 'variable';
          switch (key) {
            case 'inputVariables':
              sectionType = 'inputVariable';
              break;
            case 'outputVariables':
              sectionType = 'outputVariable';
              break;
            case 'flowNodeDurations':
              sectionType = 'flowNodeDuration';
              break;
            default:
              break;
          }

          const sectionTitle = `${t(`common.filter.types.${sectionType}-plural`)}:`;
          const sectionKey = labels[key] || key;

          return (
            <CollapsibleSection
              key={key}
              sectionKey={key}
              isSectionOpen={isSectionOpen[key]}
              sectionTitle={sectionTitle}
              toggleSectionOpen={() => toggleSectionOpen(key)}
            >
              {Object.keys(value).map((key) => {
                const label = value[key].name || getVariableLabel(key) || key;
                const switchId = `${sectionKey}:${key}`;

                return (
                  <ColumnSwitch
                    key={switchId}
                    switchId={switchId}
                    excludedColumns={excludedColumns}
                    includedColumns={includedColumns}
                    label={label}
                    onChange={onChange}
                  />
                );
              })}
            </CollapsibleSection>
          );
        }

        const label = t('report.table.rawData.' + key);

        return (
          <ColumnSwitch
            key={key}
            switchId={key}
            excludedColumns={excludedColumns}
            includedColumns={includedColumns}
            label={label}
            onChange={onChange}
          />
        );
      })}
    </fieldset>
  );
}

function getColumnNames(columns) {
  return Object.keys(columns).reduce((prev, curr) => {
    const value = columns[curr];
    if (typeof value !== 'object' || value === null) {
      return [...prev, curr];
    } else {
      return [...prev, ...Object.keys(value).map((key) => `${labels[curr]}:${key}`)];
    }
  }, []);
}

function groupColumns(columns) {
  const sectionKeys = Object.keys(labels);

  const palinColumns = Object.keys(columns).reduce((prev, columnKey) => {
    const isSection = sectionKeys.includes(columnKey);
    return !isSection ? [...prev, {key: columnKey, value: columns[columnKey]}] : prev;
  }, []);

  const sectionColumns = sectionKeys.reduce((prev, sectionKey) => {
    const isSection = columns[sectionKey];
    return isSection ? [...prev, {key: sectionKey, value: columns[sectionKey]}] : prev;
  }, []);

  return [...palinColumns, ...sectionColumns];
}
