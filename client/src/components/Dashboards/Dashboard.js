/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {withErrorHandling, withUser} from 'HOC';
import {loadEntity, updateEntity, createEntity, getCollection} from 'services';
import {isSharingEnabled, newReport} from 'config';

import {ErrorPage, LoadingIndicator, PageTitle} from 'components';

import {showError} from 'notifications';
import {t} from 'translation';

import {isAuthorizedToShareDashboard} from './service';

import DashboardView from './DashboardView';
import DashboardEdit from './DashboardEdit';

import './Dashboard.scss';

export class Dashboard extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      id: this.props.match.params.id,
      name: null,
      lastModified: null,
      lastModifier: null,
      owner: null,
      currentUserRole: null,
      loaded: false,
      redirect: '',
      tiles: [],
      availableFilters: [],
      serverError: null,
      isAuthorizedToShare: false,
      sharingEnabled: false,
      refreshRateSeconds: null,
    };
  }

  isNew = () => this.state.id === 'new';
  getTemplateParam = () => {
    return new URLSearchParams(this.props.location.search).get('template');
  };

  componentDidMount = async () => {
    this.setState({sharingEnabled: await isSharingEnabled()});

    if (this.isNew()) {
      this.createDashboard();
    } else {
      this.loadDashboard();
    }
  };

  createDashboard = async () => {
    const user = await this.props.getUser();

    const initialData = this.props.location.state;

    const modifierData = {
      lastModified: getFormattedNowDate(),
      lastModifier: user.name,
      owner: user.name,
    };

    this.setState({
      loaded: true,
      name: initialData?.name ?? t('dashboard.new'),
      ...modifierData,
      currentUserRole: 'editor',
      tiles:
        initialData?.data?.map((config) => {
          return {
            ...config,
            report: {
              ...newReport.new,
              ...modifierData,
              name: config.report.name,
              data: {
                ...newReport.new.data,
                ...config.report.data,
                definitions: initialData.definitions,
                configuration: {
                  ...newReport.new.data.configuration,
                  ...(config.report.data?.configuration ?? {}),
                  xml: initialData.xml,
                },
              },
            },
          };
        }) ?? [],
      availableFilters: [],
      isAuthorizedToShare: true,
      refreshRateSeconds: null,
    });
  };

  loadDashboard = () => {
    const templateName = this.getTemplateParam();
    this.props.mightFail(
      loadEntity(
        this.getEntityType(),
        this.state.id,
        templateName ? {template: templateName} : undefined
      ),
      async (response) => {
        const {
          id,
          name,
          lastModifier,
          currentUserRole,
          lastModified,
          owner,
          tiles,
          availableFilters,
          refreshRateSeconds,
          instantPreviewDashboard,
        } = response;

        this.setState({
          id,
          lastModifier,
          lastModified,
          owner,
          currentUserRole,
          loaded: true,
          name,
          tiles: tiles || [],
          availableFilters: availableFilters || [],
          isAuthorizedToShare: await isAuthorizedToShareDashboard(id),
          refreshRateSeconds,
          instantPreviewDashboard,
        });
      },
      (err) => {
        if (!this.state.loaded) {
          this.setState({serverError: err.status});
        } else {
          showError(err);
        }
      }
    );
  };

  getEntityType = () => {
    // Because of other apps want to still use the old magic link, we have to redirect
    // this request to the dashboard/instant
    const collectionId = getCollection(this.props.location.pathname);
    const isMagicLink = collectionId === this.state.id;
    return isMagicLink ? 'dashboard/instant' : this.props.entity;
  };

  goHome = () => {
    this.setState({
      redirect: '../../',
    });
  };

  updateDashboard = (id, name, tiles, availableFilters, refreshRateSeconds, stayInEditMode) => {
    return new Promise((resolve, reject) => {
      this.props.mightFail(
        updateEntity('dashboard', id, {
          name,
          tiles,
          availableFilters,
          refreshRateSeconds,
        }),
        () =>
          resolve(
            this.updateDashboardState(
              id,
              name,
              tiles,
              availableFilters,
              refreshRateSeconds,
              stayInEditMode
            )
          ),
        (error) => reject(showError(error))
      );
    });
  };

  updateDashboardState = async (
    id,
    name,
    tiles,
    availableFilters,
    refreshRateSeconds,
    stayInEditMode
  ) => {
    const user = await this.props.getUser();
    const redirect = this.isNew() ? `../${id}/` : './';

    const update = {
      id,
      name,
      tiles,
      availableFilters,
      isAuthorizedToShare: await isAuthorizedToShareDashboard(id),
      lastModified: getFormattedNowDate(),
      lastModifier: user.name,
      refreshRateSeconds,
    };

    if (stayInEditMode) {
      this.props.history.replace(redirect + 'edit');
    } else {
      update.redirect = redirect;
    }

    this.setState(update);
  };

  saveChanges = (name, tiles, availableFilters, refreshRateSeconds, stayInEditMode) => {
    return new Promise(async (resolve, reject) => {
      if (this.isNew()) {
        const collectionId = getCollection(this.props.location.pathname);

        const tilesIds = await Promise.all(
          tiles.map((tile) => {
            return (
              tile.id ||
              (tile.report &&
                new Promise((resolve, reject) => {
                  const {name, data, reportType, combined} = tile.report;
                  const endpoint = `report/${reportType}/${combined ? 'combined' : 'single'}`;
                  this.props.mightFail(
                    createEntity(endpoint, {collectionId, name, data}),
                    resolve,
                    reject
                  );
                })) ||
              ''
            );
          })
        );

        const savedtiles = tiles.map(({configuration, dimensions, position, type}, idx) => {
          return {
            type,
            configuration,
            dimensions,
            position,
            id: tilesIds[idx],
          };
        });

        this.props.mightFail(
          createEntity('dashboard', {
            collectionId,
            name,
            tiles: savedtiles,
            availableFilters,
            refreshRateSeconds,
          }),
          (id) =>
            resolve(
              this.updateDashboardState(
                id,
                name,
                savedtiles,
                availableFilters,
                refreshRateSeconds,
                stayInEditMode
              )
            ),
          (error) => reject(showError(error))
        );
      } else {
        resolve(
          this.updateDashboard(
            this.state.id,
            name,
            tiles,
            availableFilters,
            refreshRateSeconds,
            stayInEditMode
          )
        );
      }
    });
  };

  componentDidUpdate() {
    if (this.state.redirect) {
      this.setState({redirect: ''});
    }
  }

  render() {
    const {viewMode} = this.props.match.params;

    const {
      id,
      loaded,
      redirect,
      serverError,
      name,
      lastModified,
      currentUserRole,
      lastModifier,
      owner,
      sharingEnabled,
      isAuthorizedToShare,
      tiles,
      availableFilters,
      refreshRateSeconds,
      instantPreviewDashboard,
    } = this.state;

    if (serverError) {
      return <ErrorPage />;
    }

    if (!loaded) {
      return <LoadingIndicator />;
    }

    if (redirect) {
      return <Redirect to={redirect} />;
    }

    const commonProps = {
      name,
      refreshRateSeconds,
      lastModified,
      lastModifier,
      owner,
      id,
    };

    return (
      <div className="Dashboard">
        <PageTitle pageName={t('dashboard.label')} resourceName={name} isNew={this.isNew()} />
        {viewMode === 'edit' ? (
          <DashboardEdit
            {...commonProps}
            isNew={this.isNew()}
            saveChanges={this.saveChanges}
            initialTiles={tiles}
            initialAvailableFilters={availableFilters}
          />
        ) : (
          <DashboardView
            {...commonProps}
            sharingHidden={instantPreviewDashboard}
            sharingEnabled={sharingEnabled}
            isAuthorizedToShare={isAuthorizedToShare}
            loadDashboard={this.loadDashboard}
            onDelete={this.goHome}
            currentUserRole={currentUserRole}
            tiles={tiles}
            availableFilters={availableFilters}
          />
        )}
      </div>
    );
  }
}

export default withErrorHandling(withUser(Dashboard));

function getFormattedNowDate() {
  return format(new Date(), BACKEND_DATE_FORMAT);
}
