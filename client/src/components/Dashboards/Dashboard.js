/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import classnames from 'classnames';
import Fullscreen from 'react-full-screen';
import {default as updateState} from 'immutability-helper';
import {Link, Redirect} from 'react-router-dom';
import {withErrorHandling} from 'HOC';
import {
  checkDeleteConflict,
  toggleEntityCollection,
  loadEntity,
  createEntity,
  getEntitiesCollections
} from 'services';

import {
  Button,
  ShareEntity,
  DashboardView,
  Icon,
  Dropdown,
  Popover,
  ErrorPage,
  LoadingIndicator,
  ConfirmationModal,
  EntityNameForm,
  CollectionsDropdown,
  EditCollectionModal
} from 'components';

import {addNotification} from 'notifications';

import {themed} from 'theme';

import {
  loadDashboard,
  remove,
  update,
  loadReport,
  getSharedDashboard,
  shareDashboard,
  revokeDashboardSharing,
  isAuthorizedToShareDashboard,
  isSharingEnabled
} from './service';

import {AddButton} from './AddButton';
import {Grid} from './Grid';
import {DimensionSetter} from './DimensionSetter';
import {DeleteButton} from './DeleteButton';
import {DragBehavior} from './DragBehavior';
import {ResizeHandle} from './ResizeHandle';
import {AutoRefreshBehavior, AutoRefreshIcon} from './AutoRefresh';

import './Dashboard.scss';

export default themed(
  withErrorHandling(
    class Dashboard extends React.Component {
      constructor(props) {
        super(props);

        this.id = props.match.params.id;
        this.isNew = this.props.location.search === '?new';

        this.state = {
          name: null,
          lastModified: null,
          lastModifier: null,
          loaded: false,
          redirect: '',
          originalName: null,
          reports: [],
          originalReports: [],
          confirmModalVisible: false,
          addButtonVisible: true,
          autoRefreshInterval: null,
          fullScreenActive: false,
          serverError: null,
          isAuthorizedToShare: false,
          sharingEnabled: false,
          conflicts: [],
          deleteLoading: false,
          collections: [],
          creatingCollection: false
        };
      }

      componentDidMount = async () => {
        const sharingEnabled = await isSharingEnabled();
        await this.renderDashboard(sharingEnabled);
      };

      componentWillUnmount = () => {
        if (this.props.theme === 'dark') {
          this.props.toggleTheme();
        }
      };

      renderDashboard = async sharingEnabled => {
        await this.props.mightFail(
          loadDashboard(this.id),
          async response => {
            const {name, lastModifier, lastModified, reports} = response;

            this.setState({
              lastModifier,
              lastModified,
              loaded: true,
              name,
              originalName: name,
              reports: reports || [],
              originalReports: reports || [],
              isAuthorizedToShare: await isAuthorizedToShareDashboard(this.id),
              ...(sharingEnabled !== 'undefined' ? {sharingEnabled} : {})
            });
            await this.loadCollections();
          },
          ({status}) => {
            this.setState({
              serverError: status
            });
            return;
          }
        );
      };

      loadCollections = async () => {
        const collections = await loadEntity('collection', null, 'created');
        this.setState({collections});
      };

      openEditCollectionModal = () => {
        this.setState({creatingCollection: true});
      };

      createCollection = async collection => {
        await createEntity('collection', collection);
        await this.loadCollections();
        this.setState({creatingCollection: false});
      };

      deleteDashboard = async evt => {
        this.setState({deleteLoading: true});
        await remove(this.id);

        this.setState({
          redirect: '/'
        });
      };

      updateName = evt => {
        this.setState({
          name: evt.target.value
        });
      };

      saveChanges = async (evt, name) => {
        await this.props.mightFail(
          update(this.id, {
            name,
            reports: this.state.reports
          }),
          async () => {
            this.setState({
              originalName: this.state.name,
              originalReports: this.state.reports,
              name,
              redirect: `/dashboard/${this.id}`,
              isAuthorizedToShare: await isAuthorizedToShareDashboard(this.id)
            });
          },
          () => {
            addNotification({text: `Dashboard "${name}" could not be saved.`, type: 'error'});
          }
        );
      };

      cancelChanges = async () => {
        this.setState({
          name: this.state.originalName,
          reports: this.state.originalReports
        });
      };

      addReport = newReport => {
        this.setState({
          reports: [...this.state.reports, newReport]
        });
      };

      deleteReport = ({report: reportToRemove}) => {
        this.setState({
          reports: this.state.reports.filter(report => report !== reportToRemove)
        });
      };

      updateReport = ({report, ...changes}) => {
        const reportIdx = this.state.reports.indexOf(report);

        Object.keys(changes).forEach(prop => {
          changes[prop] = {$set: changes[prop]};
        });

        this.setState({
          reports: updateState(this.state.reports, {
            [reportIdx]: changes
          })
        });
      };

      showDeleteModal = async () => {
        this.setState({
          confirmModalVisible: true,
          deleteLoading: true
        });
        const {conflictedItems} = await checkDeleteConflict(this.id, 'dashboard');
        this.setState({conflicts: conflictedItems, deleteLoading: false});
      };

      closeConfirmModal = () => {
        this.setState({
          confirmModalVisible: false
        });
      };

      showAddButton = () => {
        this.setState({
          addButtonVisible: true
        });
      };

      hideAddButton = () => {
        this.setState({
          addButtonVisible: false
        });
      };

      toggleFullscreen = () => {
        if (this.props.theme === 'dark') {
          this.props.toggleTheme();
        }
        this.setState(prevState => {
          return {
            fullScreenActive: !prevState.fullScreenActive
          };
        });
      };

      setAutorefresh = timeout => () => {
        clearInterval(this.timer);
        if (timeout) {
          this.timer = setInterval(async () => {
            await this.renderDashboard();
          }, timeout);
        }
        this.setState({
          autoRefreshInterval: timeout
        });
      };

      autoRefreshOption = (interval, label) => {
        return (
          <Dropdown.Option
            active={this.state.autoRefreshInterval === interval}
            onClick={this.setAutorefresh(interval)}
            className="Dashboard__autoRefreshOption"
          >
            {label}
          </Dropdown.Option>
        );
      };

      changeFullScreen = fullScreenActive => {
        if (this.props.theme === 'dark') {
          this.props.toggleTheme();
        }
        this.setState({fullScreenActive});
      };

      renderEditMode = state => {
        const {name, lastModifier, lastModified, collections} = state;
        const dashboardCollections = getEntitiesCollections(collections)[this.id];

        return (
          <div className="Dashboard editMode">
            <div className="Dashboard__header">
              <EntityNameForm
                id={this.id}
                initialName={name}
                lastModified={lastModified}
                lastModifier={lastModifier}
                autofocus={this.isNew}
                entity="Dashboard"
                onSave={this.saveChanges}
                onCancel={this.cancelChanges}
              />
              <div className="subHead">
                <div className="metadata">
                  Last modified {moment(lastModified).format('lll')} by {lastModifier}
                </div>
                <CollectionsDropdown
                  entity={{id: this.id}}
                  collections={collections}
                  toggleEntityCollection={toggleEntityCollection(this.loadCollections)}
                  entityCollections={dashboardCollections}
                  setCollectionToUpdate={this.openEditCollectionModal}
                />
              </div>
            </div>
            <DashboardView
              disableReportScrolling
              loadReport={loadReport}
              reports={this.state.reports}
              reportAddons={[
                <DragBehavior
                  key="DragBehavior"
                  reports={this.state.reports}
                  updateReport={this.updateReport}
                  onDragStart={this.hideAddButton}
                  onDragEnd={this.showAddButton}
                />,
                <DeleteButton key="DeleteButton" deleteReport={this.deleteReport} />,
                <ResizeHandle
                  key="ResizeHandle"
                  reports={this.state.reports}
                  updateReport={this.updateReport}
                  onResizeStart={this.hideAddButton}
                  onResizeEnd={this.showAddButton}
                />
              ]}
            >
              <Grid reports={this.state.reports} />
              <DimensionSetter emptyRows={9} reports={this.state.reports} />
              <AddButton addReport={this.addReport} visible={this.state.addButtonVisible} />
            </DashboardView>
          </div>
        );
      };

      renderViewMode = state => {
        const {
          name,
          lastModifier,
          lastModified,
          confirmModalVisible,
          isAuthorizedToShare,
          sharingEnabled,
          conflicts,
          deleteLoading,
          collections
        } = state;

        const dashboardCollections = getEntitiesCollections(collections)[this.id];

        return (
          <Fullscreen enabled={this.state.fullScreenActive} onChange={this.changeFullScreen}>
            <div
              className={classnames('Dashboard', {
                'Dashboard--fullscreen': this.state.fullScreenActive
              })}
            >
              <div className="Dashboard__header">
                <div className="head">
                  <div className="name-container">
                    <h1 className="name">{name}</h1>
                  </div>
                  <div className="tools">
                    {!this.state.fullScreenActive && (
                      <React.Fragment>
                        <Link
                          className="tool-button edit-button"
                          to={`/dashboard/${this.id}/edit`}
                          onClick={this.setAutorefresh(null)}
                        >
                          <Button>
                            <Icon type="edit" />
                            Edit
                          </Button>
                        </Link>
                        <Button
                          onClick={this.showDeleteModal}
                          className="tool-button delete-button"
                        >
                          <Icon type="delete" />
                          Delete
                        </Button>
                        <Popover
                          className="tool-button share-button"
                          icon="share"
                          title="Share"
                          disabled={!sharingEnabled || !isAuthorizedToShare}
                          tooltip={this.createShareTooltip()}
                        >
                          <ShareEntity
                            type="dashboard"
                            resourceId={this.id}
                            shareEntity={shareDashboard}
                            revokeEntitySharing={revokeDashboardSharing}
                            getSharedEntity={getSharedDashboard}
                          />
                        </Popover>
                      </React.Fragment>
                    )}
                    {this.state.fullScreenActive && (
                      <Button onClick={this.props.toggleTheme} className="tool-button">
                        Toggle Theme
                      </Button>
                    )}
                    <Button
                      onClick={this.toggleFullscreen}
                      className="tool-button Dashboard__fullscreen-button"
                    >
                      <Icon type={this.state.fullScreenActive ? 'exit-fullscreen' : 'fullscreen'} />
                      {this.state.fullScreenActive ? ' Leave' : ' Enter'} Fullscreen
                    </Button>
                    <Dropdown
                      label={
                        <React.Fragment>
                          <AutoRefreshIcon interval={this.state.autoRefreshInterval} /> Auto Refresh
                        </React.Fragment>
                      }
                      active={!!this.state.autoRefreshInterval}
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
                    entity={{id: this.id}}
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
                onConfirm={this.deleteDashboard}
                conflict={{type: 'Delete', items: conflicts}}
                entityName={name}
                loading={deleteLoading}
              />
              <DashboardView
                loadReport={loadReport}
                reports={this.state.reports}
                reportAddons={
                  this.state.autoRefreshInterval && [
                    <AutoRefreshBehavior
                      key="autorefresh"
                      interval={this.state.autoRefreshInterval}
                    />
                  ]
                }
              >
                <DimensionSetter reports={this.state.reports} />
              </DashboardView>
            </div>
          </Fullscreen>
        );
      };

      createShareTooltip = () => {
        if (!this.state.sharingEnabled) {
          return 'Sharing is disabled per configuration';
        }
        if (!this.state.isAuthorizedToShare) {
          return (
            'You are not authorized to share the dashboard, ' +
            " because you don't have access to all reports on the dashboard!"
          );
        }
        return '';
      };

      componentDidUpdate() {
        if (this.state.redirect) {
          this.setState({redirect: ''});
        }
        if (this.isNew) {
          this.isNew = false;
        }
      }

      render() {
        const {viewMode} = this.props.match.params;

        const {loaded, redirect, serverError} = this.state;

        if (serverError) {
          return <ErrorPage />;
        }

        if (!loaded) {
          return <LoadingIndicator />;
        }

        if (redirect) {
          return <Redirect to={redirect} />;
        }

        return (
          <div className="Dashboard-container">
            {viewMode === 'edit'
              ? this.renderEditMode(this.state)
              : this.renderViewMode(this.state)}

            {this.state.creatingCollection && (
              <EditCollectionModal
                collection={{data: {entities: [this.id]}}}
                onClose={() => this.setState({creatingCollection: false})}
                onConfirm={this.createCollection}
              />
            )}
          </div>
        );
      }
    }
  )
);
