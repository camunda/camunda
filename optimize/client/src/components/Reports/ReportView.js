/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef, useState} from 'react';
import {Link, useHistory, useLocation} from 'react-router-dom';
import classnames from 'classnames';
import {Button} from '@carbon/react';
import {Edit, Share, TrashCan} from '@carbon/icons-react';

import {
  ShareEntity,
  ReportRenderer,
  Deleter,
  EntityName,
  InstanceCount,
  ReportDetails,
  DownloadButton,
  AlertsDropdown,
  EntityDescription,
  InstanceViewTable,
  Popover,
} from 'components';
import {formatters, checkDeleteConflict} from 'services';
import {t} from 'translation';
import {track} from 'tracking';
import {useUiConfig, useUser} from 'hooks';

import {shareReport, revokeReportSharing, getSharedReport} from './service';
import {CollapsibleContainer} from './CollapsibleContainer';

import './ReportView.scss';

export default function ReportView({report, error, loadReport}) {
  const [deleting, setDeletting] = useState(null);
  const {sharingEnabled, userSearchAvailable} = useUiConfig();
  const [redirect, setRedirect] = useState(false);
  const location = useLocation();
  const isProcessReport = location.pathname.includes('processes/report');
  // This calculates the max height of the raw data table to make the expand/collapse animation smooth.
  // The height value has to be in pixel to make the animation work.
  const reportContainerRef = useRef();
  const [showReportRenderer, setShowReportRenderer] = useState(true);
  const {user} = useUser();
  const history = useHistory();

  function showTable(sectionState) {
    if (sectionState !== 'maximized') {
      setShowReportRenderer(true);
    }
  }

  function handleTableExpand(currentState, newState) {
    track('changeRawDataView', {
      from: currentState,
      to: newState,
      reportType: report.data?.visualization,
    });
    setShowReportRenderer(newState !== 'maximized');
  }

  function handleTableCollapse(currentState, newState) {
    track('changeRawDataView', {
      from: currentState,
      to: newState,
      reportType: report.data?.visualization,
    });
  }

  const shouldShowCSVDownload = () => {
    return report?.data?.visualization !== 'number' && report.result?.measures.length === 1;
  };

  const constructCSVDownloadLink = () => {
    return `api/export/csv/${report.id}/${encodeURIComponent(
      formatters.formatFileName(report.name)
    )}.csv`;
  };

  const {id, name, description, currentUserRole, data} = report;

  if (redirect) {
    history.push(redirect);
  }

  const isInstantPreview = data?.instantPreviewReport;

  return (
    <div className="ReportView Report">
      <div className="reportHeader">
        <div className="head">
          <div className="info">
            <EntityName details={<ReportDetails report={report} />} name={name} />
            {description && <EntityDescription description={description} />}
          </div>
          <div className="tools">
            {!isInstantPreview && (
              <>
                {currentUserRole === 'editor' && (
                  <Button
                    as={Link}
                    to="edit"
                    hasIconOnly
                    iconDescription={t('common.edit')}
                    renderIcon={Edit}
                    className="edit-button"
                  />
                )}
                <Popover
                  className="share-button"
                  align="bottom-right"
                  trigger={
                    <Popover.Button
                      iconDescription={t('common.sharing.buttonTitle')}
                      hasIconOnly
                      renderIcon={Share}
                    />
                  }
                  isTabTip
                >
                  {sharingEnabled ? (
                    <ShareEntity
                      type="report"
                      resourceId={id}
                      shareEntity={shareReport}
                      revokeEntitySharing={revokeReportSharing}
                      getSharedEntity={getSharedReport}
                    />
                  ) : (
                    t('common.sharing.disabled')
                  )}
                </Popover>
                {userSearchAvailable && data?.visualization === 'number' && (
                  <AlertsDropdown numberReport={report} />
                )}
              </>
            )}
            {shouldShowCSVDownload() && (
              <DownloadButton
                kind="ghost"
                hasIconOnly
                href={constructCSVDownloadLink()}
                totalCount={calculateTotalEntries(report)}
                user={user}
              />
            )}
            {!isInstantPreview && currentUserRole === 'editor' && (
              <Button
                iconDescription={t('common.delete')}
                kind="ghost"
                onClick={() => setDeletting({...report, entityType: 'report'})}
                className="delete-button"
                renderIcon={TrashCan}
                hasIconOnly
              />
            )}
          </div>
        </div>
        <InstanceCount report={report} />
      </div>
      <div className="viewsContainer" ref={reportContainerRef}>
        <div className="Report__view">
          <div className={classnames('Report__content', {hidden: !showReportRenderer})}>
            {showReportRenderer && (
              <ReportRenderer error={error} report={report} loadReport={loadReport} />
            )}
          </div>
        </div>
        {!isProcessReport &&
          typeof report.result !== 'undefined' &&
          report.data?.visualization !== 'table' && (
            <CollapsibleContainer
              maxHeight={reportContainerRef.current?.offsetHeight}
              initialState="half"
              onTransitionEnd={showTable}
              onExpand={handleTableExpand}
              onCollapse={handleTableCollapse}
              title={t('report.view.rawData')}
            >
              <InstanceViewTable report={report} />
            </CollapsibleContainer>
          )}
        <Deleter
          type="report"
          entity={deleting}
          checkConflicts={({id}) => checkDeleteConflict(id, 'report')}
          onClose={() => setDeletting(null)}
          onDelete={() => setRedirect('../../')}
        />
      </div>
    </div>
  );
}

function calculateTotalEntries({result}) {
  switch (result.type) {
    case 'raw':
      return result.instanceCount;
    case 'map':
    case 'hyperMap':
      return result?.measures?.[0]?.data?.length;
    case 'number':
    default:
      return 1;
  }
}
