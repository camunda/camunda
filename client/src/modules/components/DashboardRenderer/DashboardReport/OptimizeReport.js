/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {
  ReportRenderer,
  LoadingIndicator,
  NoDataNotice,
  EntityName,
  ReportDetails,
  InstanceCount,
} from 'components';
import {withErrorHandling} from 'HOC';
import deepEqual from 'deep-equal';

import {themed} from 'theme';

import './OptimizeReport.scss';
import {t} from 'translation';

export class OptimizeReport extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loading: true,
      data: undefined,
      error: null,
    };
  }

  async componentDidMount() {
    await this.loadInitialReport();
  }

  componentDidUpdate(prevProps) {
    if (
      !deepEqual(prevProps.report, this.props.report) ||
      !deepEqual(prevProps.filter, this.props.filter)
    ) {
      this.loadInitialReport();
    }
  }

  loadInitialReport = async () => {
    this.setState({loading: true});
    await this.loadReport({});
    this.setState({loading: false});
  };

  loadReport = (params) =>
    new Promise((resolve) => {
      this.props.mightFail(
        this.props.loadReport(
          this.props.report.id ?? this.props.report.report,
          this.props.filter,
          params
        ),
        (data) => this.setState({data}, resolve),
        async (e) => {
          const errorData = await e.json();
          this.setState(
            {
              data: errorData.reportDefinition,
              error: formatError(e, errorData),
            },
            resolve
          );
        }
      );
    });

  getName = () => {
    if (this.state.data) {
      return this.state.data.name;
    }
  };

  exitDarkmode = () => {
    if (this.props.theme === 'dark') {
      this.props.toggleTheme();
    }
  };

  render() {
    const {loading, data, error} = this.state;

    if (loading) {
      return <LoadingIndicator />;
    }

    const {disableNameLink, filter, children = () => {}} = this.props;

    const reportName = this.getName();

    return (
      <div className="OptimizeReport DashboardReport__wrapper">
        <div className="titleBar" tabIndex="-1">
          <EntityName
            linkTo={!disableNameLink && `report/${data.id}/`}
            details={<ReportDetails report={data} />}
          >
            {reportName}
          </EntityName>
          <InstanceCount report={data} additionalFilter={filter} useIcon="filter" />
        </div>
        <div className="visualization">
          {error ? (
            <NoDataNotice title={error.title}>{error.text}</NoDataNotice>
          ) : (
            <ReportRenderer report={data} context="dashboard" loadReport={this.loadReport} />
          )}
        </div>
        {children({loadReportData: this.loadInitialReport})}
      </div>
    );
  }
}

export default themed(withErrorHandling(OptimizeReport));

function formatError(e, {errorCode, errorMessage}) {
  if (e.status === 403) {
    return {
      title: t('dashboard.noAuthorization'),
      text: t('dashboard.noReportAccess'),
    };
  }

  return {
    text: errorCode ? t('apiErrors.' + errorCode) : errorMessage,
  };
}
