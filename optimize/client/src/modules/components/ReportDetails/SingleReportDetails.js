/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState, useEffect} from 'react';
import classnames from 'classnames';
import {withRouter} from 'react-router-dom';
import deepEqual from 'fast-deep-equal';
import {Button} from '@carbon/react';

import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {formatters, reportConfig} from 'services';
import {t} from 'translation';
import {getOptimizeProfile} from 'config';

import RawDataModal from './RawDataModal';
import DiagramModal from './DiagramModal';
import {loadTenants} from './service';

import './SingleReportDetails.scss';

const {formatVersions, formatTenants} = formatters;

function getSelectedView(report) {
  if (report.view.entity === 'variable') {
    return `${t('report.view.variable')} ${report.view.properties[0].name}`;
  }

  const view = reportConfig.view.find(({matcher}) => matcher(report));
  let measure = '';
  if (['frequency', 'duration'].includes(report.view.properties[0])) {
    measure = report.view.properties
      .map((key) => (key === 'frequency' ? 'count' : key))
      .map((key) => (key === 'duration' && view.key === 'incident' ? 'resolutionDuration' : key))
      .map((key) => t('report.view.' + key))
      .join(` ${t('common.and')} `);
  }

  const viewString = `${view.label()} ${measure}`;
  const group = reportConfig.group.find(({matcher}) => matcher(report)).label();

  if (group.key !== 'none') {
    return t('report.viewByGroup', {
      view: viewString,
      group: group,
    });
  }

  return viewString;
}

export function SingleReportDetails({report, showReportName, mightFail, location}) {
  const [tenants, setTenants] = useState();
  const [showRawData, setShowRawData] = useState();
  const [showDiagram, setShowDiagram] = useState();
  const [optimizeProfile, setOptimizeProfile] = useState();

  const reportName = report.name;
  const definitions = report.data.definitions;
  const isShared = location.pathname.startsWith('/share');

  useEffect(() => {
    if (definitions.length && !isShared) {
      mightFail(loadTenants(definitions), setTenants, showError);
    } else {
      setTenants();
    }
  }, [definitions, isShared, mightFail]);

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  const closePopover = () => document.body.click();

  function getTenantInfoForDefinition(definition) {
    return tenants?.find(
      ({key, versions}) => key === definition.key && deepEqual(versions, definition.versions)
    )?.tenants;
  }

  return (
    <>
      <div className="SingleReportDetails">
        {showReportName && <h2>{reportName}</h2>}
        {definitions.length > 0 && (
          <div>
            <h3>{t('report.definition.process' + (definitions.length > 1 ? '-plural' : ''))}</h3>
            {definitions.map((definition, idx) => {
              const tenantInfo = getTenantInfoForDefinition(definition);
              const showOnlyTenant = tenantInfo?.length === 1 && optimizeProfile === 'ccsm';

              return (
                <div key={idx + definition.key} className="definition">
                  <h4>{definition.displayName || definition.name || definition.key}</h4>
                  <div className="info">
                    {t('common.definitionSelection.version.label')}:{' '}
                    {formatVersions(definition.versions)}
                  </div>
                  {(tenantInfo?.length > 1 || showOnlyTenant) && (
                    <div className="info">
                      {t('common.tenant.label')}:{' '}
                      {formatTenants(definition.tenantIds, tenantInfo, showOnlyTenant)}
                    </div>
                  )}
                  {!isShared && (
                    <Button
                      size="sm"
                      kind="tertiary"
                      className="modalButton"
                      onClick={() => setShowDiagram(definition)}
                    >
                      {t('common.entity.viewModel.process')}
                    </Button>
                  )}
                </div>
              );
            })}
            <hr />
          </div>
        )}
        {report.data.view && report.data.groupBy && (
          <div>
            <h3>{t('report.view.process')}</h3>
            <h4
              className={classnames({
                nowrap: report.data.view.entity === 'variable',
              })}
            >
              {getSelectedView(report.data)}
            </h4>
            {!isShared && (
              <Button
                size="sm"
                kind="tertiary"
                className="rawDataButton modalButton"
                onClick={() => setShowRawData(true)}
              >
                {t('common.entity.viewRawData')}
              </Button>
            )}
            <hr />
          </div>
        )}
      </div>
      {!!showDiagram && <DiagramModal open definition={showDiagram} onClose={closePopover} />}
      {showRawData && (
        <RawDataModal
          open
          report={report}
          name={reportName + ' - ' + t('report.view.rawData')}
          onClose={closePopover}
        />
      )}
    </>
  );
}

export default withRouter(withErrorHandling(SingleReportDetails));
