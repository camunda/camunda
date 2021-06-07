/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';
import {withRouter} from 'react-router-dom';

import {Button} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {formatters, reportConfig} from 'services';
import {t} from 'translation';

import RawDataModal from './RawDataModal';
import DiagramModal from './DiagramModal';
import {loadTenants} from './service';

import './SingleReportDetails.scss';

const {formatTenantName} = formatters;

function getSelectedView(view, groupBy, type) {
  if (view.entity === 'variable') {
    return `${t('report.view.variable')} ${view.properties[0].name}`;
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
  return data.definitions[0].key;
}
function getName(data) {
  return data.definitions[0].name;
}
function getVersions(data) {
  return data.definitions[0].versions;
}

export function SingleReportDetails({report, showReportName, mightFail, location}) {
  const [tenants, setTenants] = useState();
  const [showRawData, setShowRawData] = useState();
  const [showDiagram, setShowDiagram] = useState();

  const reportName = report.name;
  const key = getKey(report.data);
  const name = getName(report.data);
  const versions = getVersions(report.data);
  const type = report.reportType;
  const nameOrKey = name || key;
  const isShared = location.pathname.startsWith('/share');

  useEffect(() => {
    if (key && versions && !isShared) {
      mightFail(loadTenants(key, versions, type), setTenants, showError);
    } else {
      setTenants();
    }
  }, [key, versions, isShared, type, mightFail]);

  let tenantInfo;
  if (tenants && tenants.length > 1) {
    if (tenants.length === report.data.definitions[0].tenantIds.length) {
      tenantInfo = t('common.all');
    } else {
      tenantInfo = report.data.definitions[0].tenantIds
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

  const closePopover = () => document.body.click();

  return (
    <div className="SingleReportDetails">
      {showReportName && <h2>{reportName}</h2>}
      {name && (
        <dl>
          <dt>{t('report.definition.' + type)}</dt>
          <dd>{name}</dd>

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
        </dl>
      )}
      {key && versionInfo && (
        <div className="modalsButtons">
          <Button link onClick={() => setShowRawData(true)}>
            {t('common.entity.viewRawData')}
          </Button>
          <Button link onClick={() => setShowDiagram(true)}>
            {t('common.entity.viewModel.' + report.reportType)}
          </Button>
        </div>
      )}
      {name && <hr />}
      {showRawData && (
        <RawDataModal
          report={report}
          name={reportName + ' - ' + t('report.view.rawData')}
          close={closePopover}
        />
      )}
      {showDiagram && <DiagramModal report={report} name={nameOrKey} close={closePopover} />}
    </div>
  );
}

export default withRouter(withErrorHandling(SingleReportDetails));
