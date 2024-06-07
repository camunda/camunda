/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Checkbox, FormGroup, Stack} from '@carbon/react';

import {t} from 'translation';
import {getReportResult} from 'services';
import {getVariableLabel} from 'variables';

import AllColumnsButtons from './AllColumnsButtons';
import CollapsibleSection from './CollapsibleSection';
import ColumnSwitch from './ColumnSwitch';

import './ColumnSelection.scss';

const labels = {
  counts: 'count',
  inputVariables: 'input',
  outputVariables: 'output',
  variables: 'variable',
  flowNodeDurations: 'dur',
};

export default function ColumnSelection({report, onChange, disabled}) {
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
    <FormGroup legendText={t('report.config.includeTableColumn')} className="ColumnSelection">
      {disabled ? (
        t('report.updateReportPreview.cannotUpdate')
      ) : (
        <Stack gap={4}>
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
          <Checkbox
            id="includeNewVariables"
            className="includeNewVariables"
            labelText={t('report.config.includeNewVariables')}
            checked={includeNewVariables}
            onChange={({target: {checked}}) =>
              onChange({tableColumns: {includeNewVariables: {$set: checked}}})
            }
          />
          {groupedColumns.map(({key: groupKey, value: groupValue}) => {
            const isSection = typeof groupValue === 'object' && groupValue !== null;

            if (isSection && Object.keys(groupValue).length === 0) {
              return null;
            }

            if (isSection) {
              let sectionType = 'variable';
              switch (groupKey) {
                case 'inputVariables':
                  sectionType = 'inputVariable';
                  break;
                case 'outputVariables':
                  sectionType = 'outputVariable';
                  break;
                case 'flowNodeDurations':
                  sectionType = 'dur';
                  break;
                case 'counts':
                  sectionType = 'count';
                  break;
                default:
                  break;
              }

              const sectionTitle = `${t(`common.filter.types.${sectionType}-plural`)}:`;
              const sectionKey = labels[groupKey] || groupKey;

              return (
                <CollapsibleSection
                  key={groupKey}
                  sectionKey={groupKey}
                  isSectionOpen={isSectionOpen[groupKey]}
                  sectionTitle={sectionTitle}
                  toggleSectionOpen={() => toggleSectionOpen(groupKey)}
                >
                  <Stack gap={4}>
                    {Object.entries(groupValue).map(([sectionEntryKey, sectionEntryValue]) => {
                      const label = getSwitchLabel(sectionEntryKey, sectionEntryValue, sectionType);
                      const columnId = `${sectionKey}:${sectionEntryKey}`;

                      return (
                        <ColumnSwitch
                          key={columnId}
                          switchId={columnId}
                          excludedColumns={excludedColumns}
                          includedColumns={includedColumns}
                          label={label}
                          onChange={onChange}
                        />
                      );
                    })}
                  </Stack>
                </CollapsibleSection>
              );
            }
            const label = t('report.table.rawData.' + groupKey);

            return (
              <ColumnSwitch
                key={groupKey}
                switchId={groupKey}
                excludedColumns={excludedColumns}
                includedColumns={includedColumns}
                label={label}
                onChange={onChange}
              />
            );
          })}
        </Stack>
      )}
    </FormGroup>
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

function getSwitchLabel(sectionEntryKey, sectionEntryValue, sectionType) {
  if (sectionType === 'count') {
    return t('report.table.rawData.' + sectionEntryKey);
  }

  return sectionEntryValue?.name || getVariableLabel(sectionEntryKey) || sectionEntryKey;
}
