/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import moment from 'moment';
import {Link, Redirect} from 'react-router-dom';

import {
  Button,
  ShareEntity,
  ReportRenderer,
  Popover,
  Icon,
  ConfirmationModal,
  CollectionsDropdown,
  EditCollectionModal
} from 'components';

import {shareReport, revokeReportSharing, getSharedReport, isSharingEnabled} from './service';

import {
  checkDeleteConflict,
  loadEntities,
  deleteEntity,
  createEntity,
  getEntitiesCollections,
  toggleEntityCollection
} from 'services';

import './ReportView.scss';
import {t} from 'translation';

export default class ReportView extends Component {
  state = {
    confirmModalVisible: false,
    conflict: null,
    deleteLoading: false,
    collections: [],
    creatingCollection: false
  };

  async componentDidMount() {
    const sharingEnabled = await isSharingEnabled();
    await this.loadCollections();

    this.setState({sharingEnabled});
  }

  shouldShowCSVDownload = () => typeof this.props.report.result !== 'undefined';

  showDeleteModal = async () => {
    this.setState({confirmModalVisible: true, deleteLoading: true});
    let conflictState = {};
    const response = await checkDeleteConflict(this.props.report.id, 'report');
    if (response && response.conflictedItems && response.conflictedItems.length) {
      conflictState = {
        conflict: {
          type: 'Delete',
          items: response.conflictedItems
        }
      };
    }

    this.setState({
      ...conflictState,
      deleteLoading: false
    });
  };

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

  deleteReport = async evt => {
    this.setState({deleteLoading: true});
    await deleteEntity('report', this.props.report.id);

    this.setState({
      redirect: '/'
    });
  };

  closeConfirmModal = () => {
    this.setState({
      confirmModalVisible: false,
      conflict: null
    });
  };

  loadCollections = async () => {
    const collections = await loadEntities('collection', 'created');
    this.setState({collections});
  };

  openEditCollectionModal = () => {
    this.setState({creatingCollection: true});
  };

  createCollection = async name => {
    await createEntity('collection', {name, data: {entities: [this.props.report.id]}});
    await this.loadCollections();
    this.setState({creatingCollection: false});
  };

  render() {
    const {report} = this.props;
    const {
      confirmModalVisible,
      conflict,
      redirect,
      sharingEnabled,
      deleteLoading,
      collections,
      creatingCollection
    } = this.state;

    const reportCollections = getEntitiesCollections(collections)[report.id];
    const {id, name, lastModifier, lastModified} = report;

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    return (
      <>
        <ConfirmationModal
          open={confirmModalVisible}
          onClose={this.closeConfirmModal}
          onConfirm={this.deleteReport}
          conflict={conflict}
          entityName={name}
          loading={deleteLoading}
        />
        <div className="ReportView Report">
          <div className="Report__header">
            <div className="head">
              <div className="name-container">
                <h1 className="name">{name}</h1>
              </div>
              <div className="tools">
                <Link className="tool-button edit-button" to={`/report/${id}/edit`}>
                  <Button>
                    <Icon type="edit" />
                    {t('common.edit')}
                  </Button>
                </Link>
                <Button className="tool-button delete-button" onClick={this.showDeleteModal}>
                  <Icon type="delete" />
                  {t('common.delete')}
                </Button>
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
              <CollectionsDropdown
                entity={report}
                collections={collections}
                toggleEntityCollection={toggleEntityCollection(this.loadCollections)}
                entityCollections={reportCollections}
                setCollectionToUpdate={this.openEditCollectionModal}
              />
            </div>
          </div>
          <div className="Report__view">
            <div className="Report__content">
              <ReportRenderer report={report} />
            </div>
          </div>
        </div>
        {creatingCollection && (
          <EditCollectionModal
            onClose={() => this.setState({creatingCollection: false})}
            onConfirm={this.createCollection}
          />
        )}
      </>
    );
  }
}
