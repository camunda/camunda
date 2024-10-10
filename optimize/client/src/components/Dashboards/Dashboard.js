/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component} from 'react';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {withErrorHandling, withUser} from 'HOC';
import {loadEntity, updateEntity, createEntity, getCollection} from 'services';
import {isSharingEnabled, newReport} from 'config';
import {ErrorPage, Loading, PageTitle} from 'components';
import {showError} from 'notifications';
import {t} from 'translation';

import {isAuthorizedToShareDashboard} from './service';
import DashboardView from './DashboardView';
import DashboardEdit from './DashboardEdit';

import './Dashboard.scss';

export class Dashboard extends Component {
  constructor(props) {
    super(props);

    this.state = {
      id: this.props.match.params.id,
      name: null,
      description: null,
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

    // Because of other apps want to still use the old magic link,
    // we have to redirect to the dashboard/instant
    const collectionId = getCollection(this.props.location.pathname);
    const isMagicLink = collectionId === this.state.id;
    if (isMagicLink) {
      this.props.history.replace('/dashboard/instant/' + collectionId);
    }

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
      description: initialData?.description ?? null,
      ...modifierData,
      currentUserRole: 'editor',
      tiles:
        initialData?.data?.map((config) => {
          if (config.type === 'optimize_report') {
            return {
              ...config,
              report: {
                ...newReport.new,
                ...modifierData,
                name: config.report.name,
                description: config.report?.description || null,
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
          } else {
            return config;
          }
        }) ?? [],
      availableFilters: initialData?.availableFilters || [],
      isAuthorizedToShare: true,
      refreshRateSeconds: null,
    });
  };

  loadDashboard = () => {
    const templateName = this.getTemplateParam();
    this.props.mightFail(
      loadEntity(
        this.props.entity,
        this.state.id,
        templateName ? {template: templateName} : undefined
      ),
      async (response) => {
        const {
          id,
          name,
          description,
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
          description,
          tiles: tiles || [],
          availableFilters: availableFilters || [],
          isAuthorizedToShare: !instantPreviewDashboard && (await isAuthorizedToShareDashboard(id)),
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

  goHome = () => {
    this.setState({
      redirect: '../../',
    });
  };

  updateDashboard = (
    id,
    name,
    description,
    tiles,
    availableFilters,
    refreshRateSeconds,
    stayInEditMode
  ) => {
    return new Promise((resolve, reject) => {
      this.props.mightFail(
        updateEntity('dashboard', id, {
          name,
          description,
          tiles,
          availableFilters,
          refreshRateSeconds,
        }),
        () =>
          resolve(
            this.updateDashboardState(
              id,
              name,
              description,
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
    description,
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
      description,
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

  saveChanges = (
    name,
    description,
    tiles,
    availableFilters,
    refreshRateSeconds,
    stayInEditMode
  ) => {
    return new Promise(async (resolve, reject) => {
      if (this.isNew()) {
        const collectionId = getCollection(this.props.location.pathname);

        const tilesIds = await Promise.all(
          tiles.map((tile) => {
            return (
              tile.id ||
              (tile.report &&
                new Promise((resolve, reject) => {
                  const {name, description, data} = tile.report;
                  const endpoint = `report/process/single`;
                  this.props.mightFail(
                    createEntity(endpoint, {collectionId, name, description, data}),
                    resolve,
                    reject
                  );
                })) ||
              ''
            );
          })
        );

        const savedTiles = tiles.map(({configuration, dimensions, position, type}, idx) => {
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
            description,
            tiles: savedTiles,
            availableFilters,
            refreshRateSeconds,
          }),
          (id) =>
            resolve(
              this.updateDashboardState(
                id,
                name,
                description,
                savedTiles,
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
            description,
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
      description,
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
      return <Loading />;
    }

    if (redirect) {
      return this.props.history.push(redirect);
    }

    const commonProps = {
      name,
      description,
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
            isInstantDashboard={instantPreviewDashboard}
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
