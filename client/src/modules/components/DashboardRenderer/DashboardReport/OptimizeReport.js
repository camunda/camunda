/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import classnames from 'classnames';

import {ReportRenderer, LoadingIndicator} from 'components';
import {Link} from 'react-router-dom';
import {withErrorHandling} from 'HOC';

import {themed} from 'theme';

import './OptimizeReport.scss';

export default themed(
  withErrorHandling(
    class OptimizeReport extends React.Component {
      constructor(props) {
        super(props);

        this.state = {
          data: undefined
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
              data: response
            });
          },
          async e => {
            const report = (await e.json()).reportDefinition;
            if (report) {
              this.setState({data: report});
            }
            return;
          }
        );
      };

      getName = () => {
        const {name, reportDefinition} = this.state.data;

        return name || (reportDefinition && reportDefinition.name);
      };

      exitDarkmode = () => {
        if (this.props.theme === 'dark') {
          this.props.toggleTheme();
        }
      };

      render() {
        if (!this.state.data) {
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
                  to={`/report/${report.id}`}
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
              <ReportRenderer
                disableReportScrolling={disableReportScrolling}
                report={this.state.data}
                isExternal
              />
            </div>
            {children({loadReportData: this.loadReport})}
          </div>
        );
      }
    }
  )
);
