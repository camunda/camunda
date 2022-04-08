/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {
  ReportRenderer,
  LoadingIndicator,
  EntityName,
  ReportDetails,
  InstanceCount,
} from 'components';
import {withErrorHandling} from 'HOC';
import deepEqual from 'fast-deep-equal';

import {themed} from 'theme';

import './OptimizeReport.scss';

export class OptimizeReport extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loading: true,
      data: undefined,
      error: null,
      lastParams: {},
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

  loadReport = (params) => {
    this.setState({lastParams: params});
    return new Promise((resolve) => {
      this.props.mightFail(
        this.props.loadReport(
          this.props.report.id ?? this.props.report.report,
          this.props.filter,
          params
        ),
        (data) => this.setState({data, error: null}, resolve),
        (error) => {
          this.setState(
            {
              data: error.reportDefinition,
              error,
            },
            resolve
          );
        }
      );
    });
  };

  refreshReport = () => this.loadReport(this.state.lastParams);

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

    return (
      <div className="OptimizeReport DashboardReport__wrapper">
        {data && (
          <div className="titleBar" tabIndex="-1">
            <EntityName
              linkTo={!disableNameLink && `report/${data.id}/`}
              details={<ReportDetails report={data} />}
            >
              {data.name}
            </EntityName>
            <InstanceCount report={data} additionalFilter={filter} useIcon="filter" showHeader />
          </div>
        )}
        <div className="visualization">
          <ReportRenderer
            error={error}
            report={data}
            context="dashboard"
            loadReport={this.loadReport}
          />
        </div>
        {children({loadReportData: this.refreshReport})}
      </div>
    );
  }
}

export default themed(withErrorHandling(OptimizeReport));
