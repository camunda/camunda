/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';
import moment from 'moment';

import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {formatters, reportConfig} from 'services';
import {t} from 'translation';

import {loadTenants} from './service';

import './ReportDetails.scss';

const {formatTenantName} = formatters;

function getSelectedView(view, groupBy, type) {
  if (view.entity === 'variable') {
    return `${t('report.view.variable')} ${view.property.name}`;
  }

  const config = reportConfig[type];
  const selectedView = config.findSelectedOption(config.options.view, 'data', view);

  let viewString = selectedView.key
    .split('_')
    .map((key) => t('report.view.' + key))
    .join(' ');

  if (groupBy.type?.toLowerCase().includes('variable')) {
    return t('report.viewByGroup', {view: viewString, group: t('report.groupBy.' + groupBy.type)});
  }

  const selectedGroup = config.findSelectedOption(config.options.groupBy, 'data', groupBy);
  if (selectedGroup.key !== 'none') {
    return t('report.viewByGroup', {
      view: viewString,
      group: t('report.groupBy.' + selectedGroup.key.split('_')[0]),
    });
  }

  return viewString;
}

function getKey(data) {
  return data.processDefinitionKey || data.decisionDefinitionKey;
}
function getName(data) {
  return data.processDefinitionName || data.decisionDefinitionName;
}
function getVersions(data) {
  return data.processDefinitionVersions || data.decisionDefinitionVersions;
}

export function ReportDetails({report, mightFail}) {
  const [tenants, setTenants] = useState();

  const key = getKey(report.data);
  const name = getName(report.data);
  const versions = getVersions(report.data);
  const type = report.reportType;

  useEffect(() => {
    if (key && versions) {
      mightFail(loadTenants(key, versions, type), setTenants, showError);
    } else {
      setTenants();
    }
  }, [key, versions, type, mightFail]);

  let tenantInfo;
  if (tenants && tenants.length > 1) {
    if (tenants.length === report.data.tenantIds.length) {
      tenantInfo = t('common.all');
    } else {
      tenantInfo = report.data.tenantIds
        .map((tenantId) => formatTenantName(tenants.find(({id}) => id === tenantId)))
        .join(', ');
    }
  }

  let versionInfo;
  if (versions?.length === 1 && versions[0] === 'all') {
    versionInfo = t('common.all');
  } else if (versions?.length === 1 && versions[0] === 'latest') {
    versionInfo = t('common.definitionSelection.latest');
  } else if (versions) {
    versionInfo = versions.join(', ');
  }

  return (
    <div className="ReportDetails">
      <dl>
        {name && (
          <>
            <dt>{t('report.definition.' + type)}</dt>
            <dd>{name}</dd>
          </>
        )}

        {versionInfo && (
          <>
            <dt>{t('common.definitionSelection.version.label')}</dt>
            <dd>{versionInfo}</dd>
          </>
        )}

        {tenantInfo && (
          <>
            <dt>{t('common.tenant.label')}</dt>
            <dd>{tenantInfo}</dd>
          </>
        )}

        {report.data.view && report.data.groupBy && (
          <>
            <dt>{t('report.view.' + type)}</dt>
            <dd
              className={classnames({
                nowrap: report.data.view.entity === 'variable',
              })}
            >
              {getSelectedView(report.data.view, report.data.groupBy, type)}
            </dd>
          </>
        )}

        <hr />
        <dt>{t('common.entity.createdBy')}</dt>
        <dd>{report.owner}</dd>

        <dt>{t('common.entity.modifiedTitle')}</dt>
        <dd>
          {moment(report.lastModified).format('lll')}
          <br />
          {t('common.entity.byModifier', {modifier: report.lastModifier})}
        </dd>
      </dl>
    </div>
  );
}

export default withErrorHandling(ReportDetails);
