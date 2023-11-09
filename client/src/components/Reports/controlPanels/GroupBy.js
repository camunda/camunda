/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import classnames from 'classnames';
import {Button} from '@carbon/react';
import {Close} from '@carbon/icons-react';

import {t} from 'translation';
import {reportConfig, createReportUpdate} from 'services';
import {CarbonSelect} from 'components';
import {getOptimizeProfile} from 'config';

import './GroupBy.scss';

export default function GroupBy({type, report, onChange, variables}) {
  const [optimizeProfile, setOptimizeProfile] = useState();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  const reportType = type;

  if (!report.view) {
    return null;
  }

  const groups = reportConfig[type].group;
  const selectedOption = report.groupBy ? groups.find(({matcher}) => matcher(report)) : {key: null};
  const hasGroup = selectedOption.key !== 'none';
  let hasDistribution;

  if (type === 'decision') {
    hasDistribution = false;
  } else {
    hasDistribution =
      reportConfig[type].distribution.find(({matcher}) => matcher(report)).key !== 'none';
  }

  const options = groups
    .filter(
      ({visible, key}) =>
        visible(report) &&
        key !== 'none' &&
        (optimizeProfile === 'platform' ? true : !['assignee', 'candidateGroup'].includes(key))
    )
    .map(({key, enabled, label}) => {
      if (['variable', 'inputVariable', 'outputVariable'].includes(key)) {
        return (
          <CarbonSelect.Submenu
            key={key}
            label={label()}
            disabled={!enabled(report) || !variables || !variables[key]?.length}
          >
            {variables?.[key]?.map(({name, label}, idx) => {
              return (
                <CarbonSelect.Option key={idx} value={key + '_' + name} label={label || name} />
              );
            })}
          </CarbonSelect.Submenu>
        );
      } else if (['startDate', 'endDate', 'runningDate', 'evaluationDate'].includes(key)) {
        return (
          <CarbonSelect.Submenu key={key} label={label()} disabled={!enabled(report)}>
            <CarbonSelect.Option label={t('report.groupBy.automatic')} value={key + '_automatic'} />
            <CarbonSelect.Option label={t('report.groupBy.year')} value={key + '_year'} />
            <CarbonSelect.Option label={t('report.groupBy.month')} value={key + '_month'} />
            <CarbonSelect.Option label={t('report.groupBy.week')} value={key + '_week'} />
            <CarbonSelect.Option label={t('report.groupBy.day')} value={key + '_day'} />
            <CarbonSelect.Option label={t('report.groupBy.hour')} value={key + '_hour'} />
          </CarbonSelect.Submenu>
        );
      }
      return (
        <CarbonSelect.Option key={key} value={key} disabled={!enabled(report)} label={label()} />
      );
    });

  if (options.every(({props}) => props.disabled)) {
    return null;
  }

  return (
    <li className="GroupBy">
      <span className="label">{t('report.groupBy.label')}</span>
      <CarbonSelect
        className={classnames({hasNoGrouping: !hasGroup})}
        label={!hasGroup && '+ ' + t('report.addGrouping')}
        onChange={(selection) => {
          let type = selection,
            value = null;
          if (
            selection.startsWith('variable_') ||
            selection.startsWith('inputVariable_') ||
            selection.startsWith('outputVariable_')
          ) {
            [type, value] = selection.split('_');
            value = variables[type].find(({name}) => name === selection.substr(type.length + 1));
          } else if (
            selection.startsWith('startDate') ||
            selection.startsWith('endDate') ||
            selection.startsWith('runningDate') ||
            selection.startsWith('evaluationDate')
          ) {
            [type, value] = selection.split('_');
            value = {unit: value};
          }

          onChange(
            createReportUpdate(reportType, report, 'group', type, {groupBy: {value: {$set: value}}})
          );
        }}
        value={getValue(selectedOption.key, report.groupBy)}
      >
        {options}
      </CarbonSelect>
      {((hasGroup && groups.find(({key}) => key === 'none').enabled(report)) ||
        hasDistribution) && (
        <Button
          size="sm"
          kind="ghost"
          iconDescription={t('common.reset')}
          className="removeGrouping"
          onClick={() =>
            onChange(
              createReportUpdate(reportType, report, 'group', 'none', {
                groupBy: {
                  $set:
                    selectedOption.key === 'process'
                      ? {type: 'none', value: null}
                      : convertDistributionToGroup(report.distributedBy),
                },
                distributedBy: {$set: {type: 'none', value: null}},
              })
            )
          }
          hasIconOnly
          renderIcon={Close}
        />
      )}
    </li>
  );
}

function getValue(selectedOption, groupBy) {
  if (['variable', 'inputVariable', 'outputVariable'].includes(selectedOption)) {
    return selectedOption + '_' + groupBy.value.name;
  }
  if (['startDate', 'endDate', 'runningDate', 'evaluationDate'].includes(selectedOption)) {
    return selectedOption + '_' + groupBy.value.unit;
  }

  return selectedOption;
}

function convertDistributionToGroup(distributedBy) {
  switch (distributedBy.type) {
    case 'flowNode':
      return {type: 'flowNodes', value: null};
    case 'userTask':
      return {type: 'userTasks', value: null};

    default:
      return distributedBy;
  }
}
