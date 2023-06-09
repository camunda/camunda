/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {withRouter} from 'react-router-dom';
import classnames from 'classnames';

import {
  ReportRenderer,
  DashboardRenderer,
  Icon,
  LoadingIndicator,
  ErrorPage,
  EntityName,
  LastModifiedInfo,
  ReportDetails,
  InstanceCount,
  DiagramScrollLock,
  PageTitle,
} from 'components';
import {withErrorHandling} from 'HOC';
import {t} from 'translation';

import {evaluateEntity, createLoadReportCallback} from './service';

import './Sharing.scss';

export class Sharing extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      evaluationResult: null,
      loading: true,
      error: null,
    };
  }

  componentDidMount() {
    this.performEvaluation();
  }

  getId = () => {
    return this.props.match.params.id;
  };

  getType = () => {
    return this.props.match.params.type;
  };

  performEvaluation = async (params) => {
    this.props.mightFail(
      evaluateEntity(this.getId(), this.getType(), params),
      (evaluationResult) => {
        this.setState({
          evaluationResult,
        });
      },
      (error) => {
        this.setState({
          evaluationResult: error.reportDefinition,
          error,
        });
      },
      () => this.setState({loading: false})
    );
  };

  getSharingView = () => {
    if (this.getType() === 'report') {
      return (
        <ReportRenderer
          error={this.state.error}
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
          tiles={this.state.evaluationResult.tiles}
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

  getEntityUrl = () => {
    const currentUrl = window.location.href;
    const baseUrl = currentUrl.substring(0, currentUrl.indexOf('#')).replace('external/', '');

    return `${baseUrl}#/${this.getType()}/${this.state.evaluationResult.id}/`;
  };

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

    const isEmbedded = params.get('mode') === 'embed';
    const isReport = type === 'report';
    const header = params.get('header');
    const showTitle = header !== 'linkOnly';

    const SharingView = this.getSharingView();
    return (
      <div className={classnames('Sharing', {compact: isEmbedded, report: isReport})}>
        <PageTitle
          pageName={isReport ? t('report.label') : t('dashboard.label')}
          resourceName={evaluationResult.name}
        />
        {header !== 'hidden' && (
          <div className="header">
            <div className="title-container">
              {showTitle && (
                <EntityName
                  details={
                    isReport ? (
                      <ReportDetails report={evaluationResult} />
                    ) : (
                      <LastModifiedInfo entity={evaluationResult} />
                    )
                  }
                >
                  {evaluationResult.name}
                </EntityName>
              )}
              {header !== 'titleOnly' && (
                <a
                  href={this.getEntityUrl()}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={classnames('Button title-button', {
                    main: !isEmbedded,
                    small: isEmbedded,
                  })}
                >
                  <Icon type="share" renderedIn="span" />
                  <span>{isEmbedded ? t('common.open') : t('common.sharing.openInOptimize')}</span>
                </a>
              )}
            </div>
            {type === 'report' && showTitle && <InstanceCount report={evaluationResult} />}
          </div>
        )}
        <div className="content">
          {SharingView}
          {isEmbedded && (
            <a
              className="iconLink"
              href={this.getEntityUrl()}
              target="_blank"
              rel="noopener noreferrer"
            >
              <Icon type="optimize" size="20" />
            </a>
          )}
          {isEmbedded && isReport && <DiagramScrollLock />}
        </div>
      </div>
    );
  }
}

export default withErrorHandling(withRouter(Sharing));
