/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';
import {withRouter} from 'react-router-dom';
import deepEqual from 'fast-deep-equal';

import {Button} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {formatters, reportConfig} from 'services';
import {t} from 'translation';

import RawDataModal from './RawDataModal';
import DiagramModal from './DiagramModal';
import {loadTenants} from './service';

import './SingleReportDetails.scss';

const {formatVersions, formatTenants} = formatters;

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

export function SingleReportDetails({report, showReportName, mightFail, location}) {
  const [tenants, setTenants] = useState();
  const [showRawData, setShowRawData] = useState();
  const [showDiagram, setShowDiagram] = useState();

  const reportName = report.name;
  const definitions = report.data.definitions;
  const type = report.reportType;
  const isShared = location.pathname.startsWith('/share');

  useEffect(() => {
    if (definitions.length && !isShared) {
      mightFail(loadTenants(definitions, type), setTenants, showError);
    } else {
      setTenants();
    }
  }, [definitions, isShared, type, mightFail]);

  const closePopover = () => document.body.click();

  function getTenantInfoForDefinition(definition) {
    return tenants?.find(
      ({key, versions}) => key === definition.key && deepEqual(versions, definition.versions)
    )?.tenants;
  }

  return (
    <div className="SingleReportDetails">
      {showReportName && <h2>{reportName}</h2>}
      {definitions.length && (
        <div>
          <h3>{t('report.definition.' + type + (definitions.length > 1 ? '-plural' : ''))}</h3>
          {definitions.map((definition, idx) => {
            const tenantInfo = getTenantInfoForDefinition(definition);

            return (
              <div key={idx + definition.key} className="definition">
                <h4>{definition.displayName || definition.name || definition.key}</h4>
                <div className="info">
                  {t('common.definitionSelection.version.label')}:{' '}
                  {formatVersions(definition.versions)}
                </div>
                {tenantInfo?.length > 1 && (
                  <div className="info">
                    {t('common.tenant.label')}: {formatTenants(definition.tenantIds, tenantInfo)}
                  </div>
                )}
                <Button link className="modalButton" onClick={() => setShowDiagram(definition)}>
                  {t('common.entity.viewModel.' + report.reportType)}
                </Button>
              </div>
            );
          })}
          <hr />
        </div>
      )}
      {report.data.view && report.data.groupBy && (
        <div>
          <h3>{t('report.view.' + type)}</h3>
          <h4
            className={classnames({
              nowrap: report.data.view.entity === 'variable',
            })}
          >
            {getSelectedView(report.data.view, report.data.groupBy, type)}
          </h4>
          <Button className="rawDataButton modalButton" link onClick={() => setShowRawData(true)}>
            {t('common.entity.viewRawData')}
          </Button>
          <hr />
        </div>
      )}
      {showRawData && (
        <RawDataModal
          report={report}
          name={reportName + ' - ' + t('report.view.rawData')}
          close={closePopover}
        />
      )}
      {showDiagram && <DiagramModal type={type} definition={showDiagram} close={closePopover} />}
    </div>
  );
}

export default withRouter(withErrorHandling(SingleReportDetails));
