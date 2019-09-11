/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import classnames from 'classnames';

import {ReportRenderer, LoadingIndicator, NoDataNotice} from 'components';
import {Link, withRouter} from 'react-router-dom';
import {withErrorHandling} from 'HOC';

import {themed} from 'theme';

import './OptimizeReport.scss';

export default themed(
  withErrorHandling(
    withRouter(
      class OptimizeReport extends React.Component {
        constructor(props) {
          super(props);

          this.state = {
            loading: true,
            data: undefined,
            error: null
          };
        }

        async componentDidMount() {
          await this.loadReport();
        }

        loadReport = async () => {
          await this.props.mightFail(
            this.props.loadReport(this.props.report.id),
            response => {
              this.setState({
                loading: false,
                data: response
              });
            },
            async e => {
              const {errorMessage, reportDefinition} = await e.json();
              this.setState({
                loading: false,
                data: reportDefinition,
                error: !reportDefinition && errorMessage
              });
            }
          );
        };

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

          const {report, disableNameLink, disableReportScrolling, children = () => {}} = this.props;

          return (
            <div className="DashboardReport__wrapper">
              <div className="OptimizeReport__header">
                {disableNameLink ? (
                  <span className="OptimizeReport__heading">{this.getName()}</span>
                ) : (
                  <Link
                    to={`${this.props.location.pathname}report/${report.id}/`}
                    onClick={this.exitDarkmode}
                    className="OptimizeReport__heading"
                  >
                    {this.getName()}
                  </Link>
                )}
              </div>
              <div
                className={classnames('OptimizeReport__visualization', {
                  'OptimizeReport__visualization--unscrollable': disableReportScrolling
                })}
              >
                {error ? (
                  <NoDataNotice>{error}</NoDataNotice>
                ) : (
                  <ReportRenderer
                    disableReportScrolling={disableReportScrolling}
                    report={data}
                    isExternal
                  />
                )}
              </div>
              {children({loadReportData: this.loadReport})}
            </div>
          );
        }
      }
    )
  )
);
