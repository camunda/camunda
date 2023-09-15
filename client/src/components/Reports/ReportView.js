/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {Link, Redirect, useLocation} from 'react-router-dom';
import classnames from 'classnames';
import {Button, Tooltip} from '@carbon/react';
import {Download, Edit, RowCollapse, RowExpand, Share, TrashCan} from '@carbon/icons-react';

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
import {isSharingEnabled, getOptimizeProfile} from 'config';
import {formatters, checkDeleteConflict} from 'services';
import {withUser} from 'HOC';
import {t} from 'translation';
import {track} from 'tracking';

import {shareReport, revokeReportSharing, getSharedReport} from './service';

import './ReportView.scss';

export function ReportView({report, error, user, loadReport}) {
  const [deleting, setDeletting] = useState(null);
  const [sharingEnabled, setSharingEnabled] = useState(false);
  const [optimizeProfile, setOptimizeProfile] = useState(null);
  const [redirect, setRedirect] = useState(false);
  const [bottomPanelState, setBottomPanelState] = useState('half');
  const location = useLocation();
  const isProcessReport = location.pathname.includes('processes/report');

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
      setSharingEnabled(await isSharingEnabled());
    })();
  }, []);

  const shouldShowCSVDownload = () => {
    if (report.combined && typeof report.result !== 'undefined') {
      return true;
    }

    return report?.data?.visualization !== 'number' && report.result?.measures.length === 1;
  };

  const constructCSVDownloadLink = () => {
    return `api/export/csv/${report.id}/${encodeURIComponent(
      formatters.formatFileName(report.name)
    )}.csv`;
  };

  const {id, name, combined, description, currentUserRole, data} = report;

  if (redirect) {
    return <Redirect to={redirect} />;
  }

  const isInstantPreview = data?.instantPreviewReport;

  return (
    <div className="ReportView Report">
      <div className="reportHeader">
        <div className="head">
          <div className="info">
            <EntityName details={<ReportDetails report={report} />}>{name}</EntityName>
            {description && <EntityDescription description={description} />}
          </div>
          <div className="tools">
            {!isInstantPreview && (
              <>
                {currentUserRole === 'editor' && (
                  <Tooltip label={t('common.edit')} className="cds--icon-tooltip">
                    <Link
                      className="edit-button cds--btn cds--btn--icon-only cds--btn--primary"
                      to="edit"
                    >
                      <Edit />
                    </Link>
                  </Tooltip>
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
                {(optimizeProfile === 'cloud' || optimizeProfile === 'platform') &&
                  data?.visualization === 'number' && <AlertsDropdown numberReport={report} />}
              </>
            )}
            {shouldShowCSVDownload() && (
              <DownloadButton
                href={constructCSVDownloadLink()}
                totalCount={calculateTotalEntries(report)}
                user={user}
                kind="ghost"
                iconDescription={t('report.downloadCSV')}
                hasIconOnly
                renderIcon={Download}
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
      {bottomPanelState !== 'maximized' && (
        <div className="Report__view">
          <div className="Report__content">
            <ReportRenderer error={error} report={report} loadReport={loadReport} />
          </div>
        </div>
      )}
      {!isProcessReport &&
        !combined &&
        typeof report.result !== 'undefined' &&
        report.data?.visualization !== 'table' && (
          <div className={classnames('bottomPanel', bottomPanelState)}>
            <div className="toolbar">
              <b>{t('report.view.rawData')}</b>
              <div>
                {bottomPanelState !== 'maximized' && (
                  <Button
                    hasIconOnly
                    label={t('common.expand')}
                    kind="ghost"
                    onClick={() => {
                      const newState = bottomPanelState === 'minimized' ? 'half' : 'maximized';
                      track('changeRawDataView', {
                        from: bottomPanelState,
                        to: newState,
                        reportType: report.data?.visualization,
                      });
                      setBottomPanelState(newState);
                    }}
                    className="expandButton"
                    tooltipPosition="left"
                  >
                    <RowCollapse />
                  </Button>
                )}
                {bottomPanelState !== 'minimized' && (
                  <Button
                    hasIconOnly
                    label={t('common.collapse')}
                    kind="ghost"
                    onClick={() => {
                      const newState = bottomPanelState === 'maximized' ? 'half' : 'minimized';
                      track('changeRawDataView', {
                        from: bottomPanelState,
                        to: newState,
                        reportType: report.data?.visualization,
                      });
                      setBottomPanelState(newState);
                    }}
                    className="collapseButton"
                    tooltipPosition="left"
                  >
                    <RowExpand />
                  </Button>
                )}
              </div>
            </div>
            <InstanceViewTable className="bottomPanelTable" report={report} />
          </div>
        )}
      <Deleter
        type="report"
        entity={deleting}
        checkConflicts={({id}) => checkDeleteConflict(id, 'report')}
        onClose={() => setDeletting(null)}
        onDelete={() => setRedirect('../../')}
      />
    </div>
  );
}

export default withUser(ReportView);

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
