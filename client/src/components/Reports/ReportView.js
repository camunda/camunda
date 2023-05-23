/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Link, Redirect} from 'react-router-dom';

import {
  Button,
  ShareEntity,
  ReportRenderer,
  Popover,
  Icon,
  Deleter,
  EntityName,
  InstanceCount,
  ReportDetails,
  DownloadButton,
  AlertsDropdown,
  EntityDescription,
} from 'components';
import {isSharingEnabled, getOptimizeProfile} from 'config';
import {formatters, checkDeleteConflict} from 'services';
import {withUser} from 'HOC';
import {t} from 'translation';

import {shareReport, revokeReportSharing, getSharedReport} from './service';

import './ReportView.scss';

export class ReportView extends React.Component {
  state = {
    deleting: null,
    sharingEnabled: false,
    optimizeProfile: null,
  };

  async componentDidMount() {
    this.setState({
      optimizeProfile: await getOptimizeProfile(),
      sharingEnabled: await isSharingEnabled(),
    });
  }

  shouldShowCSVDownload = () => {
    const {report} = this.props;

    if (report.combined && typeof report.result !== 'undefined') {
      return true;
    }

    return this.props.report.result?.measures.length === 1;
  };

  constructCSVDownloadLink = () => {
    return `api/export/csv/${this.props.report.id}/${encodeURIComponent(
      formatters.formatFileName(this.props.report.name)
    )}.csv`;
  };

  render() {
    const {report, error, user, loadReport} = this.props;
    const {redirect, sharingEnabled, optimizeProfile, deleting} = this.state;

    const {id, name, description, currentUserRole, data} = report;
    const isInstantPreviewReport = data?.instantPreviewReport;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="ReportView Report">
        <div className="reportHeader">
          <div className="head">
            <div className="info">
              <EntityName details={<ReportDetails report={report} />}>{name}</EntityName>
              {description && <EntityDescription description={description} />}
            </div>
            <div className="tools">
              {!isInstantPreviewReport && currentUserRole === 'editor' && (
                <>
                  <Link className="tool-button edit-button" to="edit">
                    <Button main tabIndex="-1">
                      <Icon type="edit" />
                      {t('common.edit')}
                    </Button>
                  </Link>
                  <Button
                    main
                    className="tool-button delete-button"
                    onClick={() => this.setState({deleting: {...report, entityType: 'report'}})}
                  >
                    <Icon type="delete" />
                    {t('common.delete')}
                  </Button>
                </>
              )}
              {!isInstantPreviewReport && (
                <Popover
                  main
                  className="tool-button share-button"
                  icon="share"
                  title={t('common.sharing.buttonTitle')}
                  tooltip={!sharingEnabled ? t('common.sharing.disabled') : ''}
                  disabled={!sharingEnabled}
                >
                  <ShareEntity
                    type="report"
                    resourceId={id}
                    shareEntity={shareReport}
                    revokeEntitySharing={revokeReportSharing}
                    getSharedEntity={getSharedReport}
                  />
                </Popover>
              )}
              {(optimizeProfile === 'cloud' || optimizeProfile === 'platform') &&
                data?.visualization === 'number' && <AlertsDropdown numberReport={report} />}
              {this.shouldShowCSVDownload() && (
                <DownloadButton
                  main
                  href={this.constructCSVDownloadLink()}
                  totalCount={calculateTotalEntries(report)}
                  user={user}
                >
                  <Icon type="save" />
                  {t('report.downloadCSV')}
                </DownloadButton>
              )}
            </div>
          </div>
          <InstanceCount report={report} />
        </div>
        <div className="Report__view">
          <div className="Report__content">
            <ReportRenderer error={error} report={report} loadReport={loadReport} />
          </div>
        </div>
        <Deleter
          type="report"
          entity={deleting}
          checkConflicts={({id}) => checkDeleteConflict(id, 'report')}
          onClose={() => this.setState({deleting: null})}
          onDelete={() => this.setState({redirect: '../../'})}
        />
      </div>
    );
  }
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
