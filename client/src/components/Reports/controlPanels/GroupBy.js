/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import classnames from 'classnames';

import {t} from 'translation';
import {formatters, reportConfig, createReportUpdate} from 'services';
import {Select, Button, Icon, Input} from 'components';
import {getOptimizeProfile} from 'config';

import './GroupBy.scss';

export default function GroupBy({type, report, onChange, variables}) {
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [searchQuery, setSearchQuery] = useState('');

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
        const matchQuery = ({name, label}) =>
          (label || name).toLowerCase().includes(searchQuery.toLowerCase());

        return (
          <Select.Submenu
            key={key}
            label={label()}
            disabled={!enabled(report) || !variables || !variables[key]?.length}
            onClose={() => setSearchQuery('')}
          >
            <div className="searchContainer">
              <Icon className="searchIcon" type="search" />
              <Input
                type="text"
                className="searchInput"
                placeholder={t('report.groupBy.searchForVariable')}
                value={searchQuery}
                onChange={({target: {value}}) => setSearchQuery(value)}
                onClick={(evt) => evt.stopPropagation()}
                onKeyDown={(evt) => evt.stopPropagation()}
                // We progmatically trigger a click on the variable submenu on focus
                // This prevents closing it when moving the mouse outside it
                onFocus={(evt) => evt.target.closest('.Submenu:not(.fixed)')?.click()}
              />
            </div>
            {variables?.[key]?.map(({name, label}, idx) => {
              return (
                <Select.Option
                  className={classnames({
                    hidden: !matchQuery({name, label}),
                  })}
                  key={idx}
                  value={key + '_' + name}
                  label={label || name}
                >
                  {formatters.getHighlightedText(label || name, searchQuery)}
                </Select.Option>
              );
            })}
            {variables?.[key]?.filter(matchQuery).length === 0 && (
              <Select.Option disabled>{t('common.filter.variableModal.noVariables')}</Select.Option>
            )}
          </Select.Submenu>
        );
      } else if (['startDate', 'endDate', 'runningDate', 'evaluationDate'].includes(key)) {
        return (
          <Select.Submenu key={key} label={label()} disabled={!enabled(report)}>
            <Select.Option value={key + '_automatic'}>
              {t('report.groupBy.automatic')}
            </Select.Option>
            <Select.Option value={key + '_year'}>{t('report.groupBy.year')}</Select.Option>
            <Select.Option value={key + '_month'}>{t('report.groupBy.month')}</Select.Option>
            <Select.Option value={key + '_week'}>{t('report.groupBy.week')}</Select.Option>
            <Select.Option value={key + '_day'}>{t('report.groupBy.day')}</Select.Option>
            <Select.Option value={key + '_hour'}>{t('report.groupBy.hour')}</Select.Option>
          </Select.Submenu>
        );
      }
      return (
        <Select.Option key={key} value={key} disabled={!enabled(report)}>
          {label()}
        </Select.Option>
      );
    });

  if (options.every(({props}) => props.disabled)) {
    return null;
  }

  return (
    <li className="GroupBy">
      <span className="label">{t('report.groupBy.label')}</span>
      <Select
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
      </Select>
      {((hasGroup && groups.find(({key}) => key === 'none').enabled(report)) ||
        hasDistribution) && (
        <Button
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
        >
          <Icon type="close-small" />
        </Button>
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
