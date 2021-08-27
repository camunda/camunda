/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import classnames from 'classnames';
import update from 'immutability-helper';

import {Select, Button, Icon} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {loadVariables, reportConfig} from 'services';

export function DistributedBy({report, onChange, mightFail}) {
  const {definitions, distributedBy, view, groupBy, visualization, configuration} = report.data;
  const {key, versions, tenantIds} = definitions?.[0] ?? {};
  const [variables, setVariables] = useState([]);

  useEffect(() => {
    if (isInstanceDateReport(view, groupBy)) {
      mightFail(
        loadVariables([
          {
            processDefinitionKey: key,
            processDefinitionVersions: versions,
            tenantIds,
          },
        ]),
        setVariables,
        showError
      );
    }
  }, [view, groupBy, mightFail, key, versions, tenantIds, setVariables]);

  if (canDistributeData(view, groupBy, definitions)) {
    const value = getValue(distributedBy);
    const hasDistribution = value !== 'none';

    return (
      <li className="DistributedBy GroupBy">
        <span className="label">{t('common.and')}</span>
        <Select
          className={classnames('ReportSelect', {hasNoGrouping: !hasDistribution})}
          key={variables.length}
          value={value}
          label={!hasDistribution && '+ Add grouping'}
          onChange={(value) => {
            const change = {distributedBy: {$set: {type: value, value: null}}};

            if (
              isInstanceDateReport(view, groupBy) &&
              value !== 'none' &&
              value.startsWith('variable_')
            ) {
              const variable = variables.find(
                ({name}) => name === value.substr('variable_'.length)
              );
              change.distributedBy.$set = {type: 'variable', value: variable};
            }

            if (isInstanceVariableReport(view, groupBy) && value !== 'none') {
              const [type, unit] = value.split('_');
              change.distributedBy.$set = {type, value: {unit}};
            }

            if (
              !reportConfig.process.isAllowed(
                update(report, {data: change}),
                view,
                groupBy,
                visualization
              )
            ) {
              change.visualization = {$set: 'bar'};
            }

            if (value === 'process' && configuration.aggregationTypes.includes('median')) {
              if (configuration.aggregationTypes.length === 1) {
                change.configuration = {aggregationTypes: {$set: ['avg']}};
              } else {
                change.configuration = {
                  aggregationTypes: {
                    $set: configuration.aggregationTypes.filter((entry) => entry !== 'median'),
                  },
                };
              }
            }

            onChange(change, true);
          }}
        >
          {getOptionsFor(view.entity, groupBy.type, variables, definitions)}
        </Select>
        {hasDistribution && (
          <Button
            className="removeGrouping"
            onClick={() => onChange({distributedBy: {$set: {type: 'none', value: null}}}, true)}
          >
            <Icon type="close-small" />
          </Button>
        )}
      </li>
    );
  }
  return null;
}

function getValue(distributedBy) {
  if (distributedBy.type === 'variable') {
    return 'variable_' + distributedBy.value.name;
  }

  if (['startDate', 'endDate'].includes(distributedBy.type)) {
    const {value, type} = distributedBy;
    return type + '_' + value.unit;
  }

  return distributedBy.type;
}

function canDistributeData(view, groupBy, definitions) {
  if (!view || !groupBy || groupBy.type === 'none') {
    return false;
  }

  if (definitions.length > 1 && !['incident', 'variable', null].includes(view.entity)) {
    return true;
  }

  if (view.entity === 'userTask') {
    return true;
  }

  if (
    view.entity === 'flowNode' &&
    ['startDate', 'endDate', 'duration', 'variable'].includes(groupBy.type)
  ) {
    return true;
  }

  if (isInstanceDateReport(view, groupBy)) {
    return true;
  }

  if (view.entity === 'processInstance' && groupBy.type === 'variable') {
    return true;
  }
}

function getOptionsFor(view, groupBy, variables, definitions) {
  const options = [];

  if (view === 'userTask') {
    if (['userTasks', 'startDate', 'endDate'].includes(groupBy)) {
      options.push(
        <Select.Option key="assignee" value="assignee">
          {t('report.groupBy.userAssignee')}
        </Select.Option>,
        <Select.Option key="candidateGroup" value="candidateGroup">
          {t('report.groupBy.userGroup')}
        </Select.Option>
      );
    }

    if (groupBy !== 'userTasks') {
      options.push(
        <Select.Option key="userTask" value="userTask">
          {t('report.view.userTask')}
        </Select.Option>
      );
    }
  }

  if (view === 'flowNode') {
    if (['startDate', 'endDate', 'duration', 'variable'].includes(groupBy)) {
      options.push(
        <Select.Option key="flowNode" value="flowNode">
          {t('report.view.fn')}
        </Select.Option>
      );
    }
  }

  if (view === 'processInstance') {
    if (groupBy === 'startDate' || groupBy === 'endDate') {
      options.push(
        <Select.Submenu key="variable" label="Variable">
          {variables.map(({name}, idx) => {
            return (
              <Select.Option key={idx} value={'variable_' + name}>
                {name}
              </Select.Option>
            );
          })}
        </Select.Submenu>
      );
    }

    if (groupBy === 'variable') {
      ['startDate', 'endDate'].map((key) =>
        options.push(
          <Select.Submenu key={key} label={t('report.groupBy.' + key)}>
            <Select.Option value={key + '_automatic'}>{t('common.unit.automatic')}</Select.Option>
            <Select.Option value={key + '_hour'}>
              {t('common.unit.hour.label-plural')}
            </Select.Option>
            <Select.Option value={key + '_day'}>{t('common.unit.day.label-plural')}</Select.Option>
            <Select.Option value={key + '_week'}>
              {t('common.unit.week.label-plural')}
            </Select.Option>
            <Select.Option value={key + '_month'}>
              {t('common.unit.month.label-plural')}
            </Select.Option>
            <Select.Option value={key + '_year'}>
              {t('common.unit.year.label-plural')}
            </Select.Option>
          </Select.Submenu>
        )
      );
    }
  }

  if (definitions.length > 1) {
    options.push(
      <Select.Option key="process" value="process">
        {t('common.process.label')}
      </Select.Option>
    );
  }

  return options;
}

function isInstanceDateReport(view, groupBy) {
  return (
    view?.entity === 'processInstance' &&
    (groupBy?.type === 'startDate' || groupBy?.type === 'endDate')
  );
}

function isInstanceVariableReport(view, groupBy) {
  return view?.entity === 'processInstance' && groupBy?.type === 'variable';
}

export default withErrorHandling(DistributedBy);
