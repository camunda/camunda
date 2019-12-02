/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {Button, ShareEntity, ReportRenderer, Popover, Icon, Deleter} from 'components';
import {isSharingEnabled} from 'config';

import {shareReport, revokeReportSharing, getSharedReport} from './service';

import {checkDeleteConflict} from 'services';

import './ReportView.scss';
import {t} from 'translation';

export default class ReportView extends React.Component {
  state = {
    deleting: null
  };

  async componentDidMount() {
    const sharingEnabled = await isSharingEnabled();
    this.setState({sharingEnabled});
  }

  shouldShowCSVDownload = () => typeof this.props.report.result !== 'undefined';

  constructCSVDownloadLink = () => {
    const {excludedColumns} = this.props.report.data.configuration;

    const queryString = excludedColumns
      ? `?excludedColumns=${excludedColumns
          .map(column => column.replace('var__', 'variable:'))
          .join(',')}`
      : '';

    return `api/export/csv/${this.props.report.id}/${encodeURIComponent(
      this.props.report.name.replace(/\s/g, '_')
    )}.csv${queryString}`;
  };

  render() {
    const {report} = this.props;
    const {redirect, sharingEnabled, deleting} = this.state;

    const {id, name, currentUserRole, lastModifier, lastModified} = report;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <div className="ReportView Report">
        <div className="Report__header">
          <div className="head">
            <div className="name-container">
              <h1 className="name">{name}</h1>
            </div>
            <div className="tools">
              {currentUserRole === 'editor' && (
                <>
                  <Link className="tool-button edit-button" to="edit">
                    <Button>
                      <Icon type="edit" />
                      {t('common.edit')}
                    </Button>
                  </Link>
                  <Button
                    className="tool-button delete-button"
                    onClick={() => this.setState({deleting: {...report, entityType: 'report'}})}
                  >
                    <Icon type="delete" />
                    {t('common.delete')}
                  </Button>
                </>
              )}
              <Popover
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
              {this.shouldShowCSVDownload() && (
                <a
                  className="Report__tool-button Report__csv-download-button"
                  href={this.constructCSVDownloadLink()}
                >
                  <Button>
                    <Icon type="save" />
                    {t('report.downloadCSV')}
                  </Button>
                </a>
              )}
            </div>
          </div>
          <div className="subHead">
            <div className="metadata">
              {t('common.entity.modified')} {moment(lastModified).format('lll')}{' '}
              {t('common.entity.by')} {lastModifier}
            </div>
          </div>
        </div>
        <div className="Report__view">
          <div className="Report__content">
            <ReportRenderer report={report} />
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
