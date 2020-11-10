/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './Sharing.scss';
import {
  ReportRenderer,
  DashboardRenderer,
  Icon,
  Button,
  LoadingIndicator,
  ErrorPage,
  EntityName,
  LastModifiedInfo,
  ReportDetails,
  InstanceCount,
  DiagramScrollLock,
} from 'components';
import {Link, withRouter} from 'react-router-dom';
import {evaluateEntity, createLoadReportCallback} from './service';
import {t} from 'translation';

export class Sharing extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      evaluationResult: null,
      loading: true,
    };

    this.performEvaluation();
  }

  getId = () => {
    return this.props.match.params.id;
  };

  getType = () => {
    return this.props.match.params.type;
  };

  performEvaluation = async (params) => {
    const evaluationResult = await evaluateEntity(this.getId(), this.getType(), params);

    this.setState({
      evaluationResult,
      loading: false,
    });
  };

  getSharingView = () => {
    if (this.getType() === 'report') {
      return (
        <ReportRenderer
          report={this.state.evaluationResult}
          context="shared"
          loadReport={this.performEvaluation}
        />
      );
    } else {
      const params = new URLSearchParams(this.props.location.search);
      const filter = params.get('filter');

      return (
        <DashboardRenderer
          loadReport={createLoadReportCallback(this.getId())}
          reports={this.state.evaluationResult.reports}
          filter={filter && JSON.parse(filter)}
          addons={[<DiagramScrollLock key="diagramScrollLock" />]}
          disableNameLink
        />
      );
    }
  };

  hasValidType(type) {
    return type === 'report' || type === 'dashboard';
  }

  render() {
    const {loading, evaluationResult} = this.state;
    const type = this.getType();
    const params = new URLSearchParams(this.props.location.search);

    if (loading) {
      return <LoadingIndicator />;
    }

    if (!evaluationResult || !this.hasValidType(type)) {
      return <ErrorPage noLink />;
    }

    const SharingView = this.getSharingView();
    return (
      <div className="Sharing">
        <div className="header">
          <div className="title-container">
            <EntityName
              details={
                type === 'report' ? (
                  <ReportDetails report={evaluationResult} />
                ) : (
                  <LastModifiedInfo entity={evaluationResult} />
                )
              }
            >
              {evaluationResult.name}
            </EntityName>
            <Link
              target="_blank"
              to={`/${this.getType()}/${this.state.evaluationResult.id}/`}
              className="title-button"
            >
              <Button main>
                <Icon type="share" renderedIn="span" />
                <span>{t('common.sharing.openInOptimize')}</span>
              </Button>
            </Link>
          </div>
          {type === 'report' && <InstanceCount report={evaluationResult} />}
        </div>
        <div className="content">
          {SharingView}
          {type === 'report' && params.get('mode') === 'embed' && <DiagramScrollLock />}
        </div>
      </div>
    );
  }
}

export default withRouter(Sharing);
