/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';
import {withErrorHandling} from 'HOC';
import {loadEntity, deleteEntity, updateEntity} from 'services';

import {ErrorPage, LoadingIndicator} from 'components';

import {addNotification} from 'notifications';

import {isAuthorizedToShareDashboard, isSharingEnabled} from './service';

import DashboardView from './DashboardView';
import DashboardEdit from './DashboardEdit';

import './Dashboard.scss';

export default withErrorHandling(
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
        reports: [],
        serverError: null,
        isAuthorizedToShare: false,
        sharingEnabled: false
      };
    }

    componentDidMount = async () => {
      const sharingEnabled = await isSharingEnabled();
      await this.loadDashboard(sharingEnabled);
    };

    loadDashboard = async sharingEnabled => {
      await this.props.mightFail(
        loadEntity('dashboard', this.id),
        async response => {
          const {name, lastModifier, lastModified, reports} = response;

          this.setState({
            lastModifier,
            lastModified,
            loaded: true,
            name,
            reports: reports || [],
            isAuthorizedToShare: await isAuthorizedToShareDashboard(this.id),
            ...(sharingEnabled !== 'undefined' ? {sharingEnabled} : {})
          });
        },
        ({status}) => {
          this.setState({
            serverError: status
          });
        }
      );
    };

    deleteDashboard = async evt => {
      await deleteEntity('dashboard', this.id);

      this.setState({
        redirect: '/'
      });
    };

    saveChanges = (name, reports) => {
      this.props.mightFail(
        updateEntity('dashboard', this.id, {
          name,
          reports
        }),
        async () => {
          this.setState({
            name,
            reports,
            redirect: `/dashboard/${this.id}`,
            isAuthorizedToShare: await isAuthorizedToShareDashboard(this.id)
          });
        },
        () => {
          addNotification({text: `Dashboard "${name}" could not be saved.`, type: 'error'});
        }
      );
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

      const {
        loaded,
        redirect,
        serverError,
        name,
        lastModified,
        lastModifier,
        sharingEnabled,
        isAuthorizedToShare,
        reports
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
        lastModified,
        lastModifier,
        id: this.id
      };

      return (
        <div className="Dashboard">
          {viewMode === 'edit' ? (
            <DashboardEdit
              {...commonProps}
              isNew={this.isNew}
              saveChanges={this.saveChanges}
              initialReports={this.state.reports}
            />
          ) : (
            <DashboardView
              {...commonProps}
              sharingEnabled={sharingEnabled}
              isAuthorizedToShare={isAuthorizedToShare}
              loadDashboard={this.loadDashboard}
              deleteDashboard={this.deleteDashboard}
              reports={reports}
            />
          )}
        </div>
      );
    }
  }
);
