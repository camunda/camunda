/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import classnames from 'classnames';
import Fullscreen from 'react-full-screen';
import {Link} from 'react-router-dom';
import {
  checkDeleteConflict,
  toggleEntityCollection,
  createEntity,
  evaluateReport,
  getEntitiesCollections,
  loadEntities
} from 'services';

import {
  Button,
  ShareEntity,
  DashboardRenderer,
  Icon,
  Dropdown,
  Popover,
  ConfirmationModal,
  CollectionsDropdown,
  EditCollectionModal
} from 'components';

import {themed} from 'theme';

import {getSharedDashboard, shareDashboard, revokeDashboardSharing} from './service';

import {DimensionSetter} from './DimensionSetter';
import {AutoRefreshBehavior, AutoRefreshIcon} from './AutoRefresh';

import './DashboardView.scss';

export default themed(
  class DashboardView extends React.Component {
    state = {
      fullScreenActive: false,
      autoRefreshInterval: null,
      creatingCollection: false,
      confirmModalVisible: false,
      conflicts: [],
      collections: [],
      deleteLoading: false
    };

    componentDidMount() {
      this.loadCollections();
    }

    componentWillUnmount = () => {
      if (this.props.theme === 'dark') {
        this.props.toggleTheme();
      }
    };

    changeFullScreen = fullScreenActive => {
      if (this.props.theme === 'dark') {
        this.props.toggleTheme();
      }
      this.setState({fullScreenActive});
    };

    setAutorefresh = timeout => {
      clearInterval(this.timer);
      if (timeout) {
        this.timer = setInterval(this.props.loadDashboard, timeout);
      }
      this.setState({
        autoRefreshInterval: timeout
      });
    };

    showDeleteModal = async () => {
      this.setState({
        confirmModalVisible: true,
        deleteLoading: true
      });
      const {conflictedItems} = await checkDeleteConflict(this.props.id, 'dashboard');
      this.setState({conflicts: conflictedItems, deleteLoading: false});
    };

    getShareTooltip = () => {
      if (!this.props.sharingEnabled) {
        return 'Sharing is disabled per configuration';
      }
      if (!this.props.isAuthorizedToShare) {
        return (
          'You are not authorized to share the dashboard, ' +
          " because you don't have access to all reports on the dashboard!"
        );
      }
      return '';
    };

    autoRefreshOption = (interval, label) => {
      return (
        <Dropdown.Option
          active={this.state.autoRefreshInterval === interval}
          onClick={() => this.setAutorefresh(interval)}
        >
          {label}
        </Dropdown.Option>
      );
    };

    openEditCollectionModal = () => {
      this.setState({creatingCollection: true});
    };

    loadCollections = async () => {
      this.setState({collections: await loadEntities('collection', 'created')});
    };

    closeConfirmModal = () => {
      this.setState({
        confirmModalVisible: false
      });
    };

    createCollection = async name => {
      await createEntity('collection', {name, data: {entities: [this.props.id]}});
      await this.loadCollections();
      this.setState({creatingCollection: false});
    };

    render() {
      const {
        id,
        name,
        lastModifier,
        lastModified,
        isAuthorizedToShare,
        sharingEnabled,
        reports
      } = this.props;

      const {
        collections,
        fullScreenActive,
        autoRefreshInterval,
        confirmModalVisible,
        conflicts,
        deleteLoading,
        creatingCollection
      } = this.state;

      const dashboardCollections = getEntitiesCollections(collections)[id];

      return (
        <Fullscreen enabled={fullScreenActive} onChange={this.changeFullScreen}>
          <div
            className={classnames('DashboardView', {
              fullscreen: fullScreenActive
            })}
          >
            <div className="header">
              <div className="head">
                <div className="name-container">
                  <h1 className="name">{name}</h1>
                </div>
                <div className="tools">
                  {!fullScreenActive && (
                    <React.Fragment>
                      <Link
                        className="tool-button edit-button"
                        to={`/dashboard/${id}/edit`}
                        onClick={() => this.setAutorefresh(null)}
                      >
                        <Button>
                          <Icon type="edit" />
                          Edit
                        </Button>
                      </Link>
                      <Button onClick={this.showDeleteModal} className="tool-button delete-button">
                        <Icon type="delete" />
                        Delete
                      </Button>
                      <Popover
                        className="tool-button share-button"
                        icon="share"
                        title="Share"
                        disabled={!sharingEnabled || !isAuthorizedToShare}
                        tooltip={this.getShareTooltip()}
                      >
                        <ShareEntity
                          type="dashboard"
                          resourceId={id}
                          shareEntity={shareDashboard}
                          revokeEntitySharing={revokeDashboardSharing}
                          getSharedEntity={getSharedDashboard}
                        />
                      </Popover>
                    </React.Fragment>
                  )}
                  {fullScreenActive && (
                    <Button onClick={this.props.toggleTheme} className="tool-button theme-toggle">
                      Toggle Theme
                    </Button>
                  )}
                  <Button
                    onClick={() => this.changeFullScreen(!this.state.fullScreenActive)}
                    className="tool-button fullscreen-button"
                  >
                    <Icon type={fullScreenActive ? 'exit-fullscreen' : 'fullscreen'} />
                    {fullScreenActive ? ' Leave' : ' Enter'} Fullscreen
                  </Button>
                  <Dropdown
                    label={
                      <React.Fragment>
                        <AutoRefreshIcon interval={autoRefreshInterval} /> Auto Refresh
                      </React.Fragment>
                    }
                    active={!!autoRefreshInterval}
                  >
                    {this.autoRefreshOption(null, 'Off')}
                    {this.autoRefreshOption(1 * 60 * 1000, '1 Minute')}
                    {this.autoRefreshOption(5 * 60 * 1000, '5 Minutes')}
                    {this.autoRefreshOption(10 * 60 * 1000, '10 Minutes')}
                    {this.autoRefreshOption(15 * 60 * 1000, '15 Minutes')}
                    {this.autoRefreshOption(30 * 60 * 1000, '30 Minutes')}
                    {this.autoRefreshOption(60 * 60 * 1000, '60 Minutes')}
                  </Dropdown>
                </div>
              </div>
              <div className="subHead">
                <div className="metadata">
                  Last modified {moment(lastModified).format('lll')} by {lastModifier}
                </div>
                <CollectionsDropdown
                  entity={{id}}
                  collections={collections}
                  toggleEntityCollection={toggleEntityCollection(this.loadCollections)}
                  entityCollections={dashboardCollections}
                  setCollectionToUpdate={this.openEditCollectionModal}
                />
              </div>
            </div>
            <ConfirmationModal
              open={confirmModalVisible}
              onClose={this.closeConfirmModal}
              onConfirm={this.props.deleteDashboard}
              conflict={{type: 'Delete', items: conflicts}}
              entityName={name}
              loading={deleteLoading}
            />
            <DashboardRenderer
              loadReport={evaluateReport}
              reports={reports}
              reportAddons={
                autoRefreshInterval && [
                  <AutoRefreshBehavior key="autorefresh" interval={autoRefreshInterval} />
                ]
              }
            >
              <DimensionSetter reports={reports} />
            </DashboardRenderer>
          </div>
          {creatingCollection && (
            <EditCollectionModal
              collection={{}}
              onClose={() => this.setState({creatingCollection: false})}
              onConfirm={this.createCollection}
            />
          )}
        </Fullscreen>
      );
    }
  }
);
