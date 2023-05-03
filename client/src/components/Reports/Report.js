/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {format, BACKEND_DATE_FORMAT} from 'dates';
import {withErrorHandling, withUser} from 'HOC';
import {ErrorPage, LoadingIndicator, PageTitle} from 'components';
import {evaluateReport} from 'services';
import {newReport} from 'config';

import ReportEdit from './ReportEdit';
import ReportView from './ReportView';

import './Report.scss';
import {t} from 'translation';

export class Report extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      report: undefined,
      collections: [],
      creatingCollection: false,
      serverError: null,
    };
  }

  getId = () => this.props.match.params.id;
  isNew = () => ['new', 'new-combined', 'new-decision'].includes(this.getId());

  componentDidMount = () => {
    if (this.isNew()) {
      this.createReport();
    } else {
      this.loadReport();
    }
  };

  createReport = async () => {
    const {location} = this.props;

    const user = await this.props.getUser();
    const now = getFormattedNowDate();

    const report = {
      ...newReport[this.getId()],
      name: t('report.new'),
      description: null,
      lastModified: now,
      created: now,
      lastModifier: user.name,
      owner: user.name,
    };

    if (this.getId() === 'new' && location.state) {
      // creating a new process report from template
      const {name, description, data} = location.state;

      report.name = name;
      report.description = description;
      report.data = {
        ...report.data,
        ...data,
        configuration: {...report.data.configuration, ...data.configuration},
      };
    }

    this.setState({
      report,
    });
  };

  loadReport = (params, report = this.state.report) =>
    this.props.mightFail(
      evaluateReport(report ?? this.getId(), [], params),
      async (response) => {
        this.setState({
          report: response,
        });
      },
      async (serverError) => {
        if (serverError.reportDefinition) {
          this.setState({
            report: serverError.reportDefinition,
            serverError,
          });
        } else {
          this.setState({serverError});
        }
      }
    );

  render() {
    const {report, serverError} = this.state;

    if (!report && serverError) {
      return <ErrorPage />;
    }

    if (!report) {
      return <LoadingIndicator />;
    }

    const {viewMode} = this.props.match.params;

    return (
      <div className="Report-container">
        <PageTitle pageName={t('report.label')} resourceName={report?.name} isNew={this.isNew()} />
        {viewMode === 'edit' ? (
          <ReportEdit
            error={serverError}
            isNew={this.isNew()}
            updateOverview={(newReport, serverError) => {
              const {mightFail, getUser} = this.props;

              mightFail(getUser(), (user) =>
                this.setState({
                  serverError,
                  report: {
                    ...newReport,
                    lastModified: getFormattedNowDate(),
                    lastModifier: user.name,
                  },
                })
              );
            }}
            report={report}
          />
        ) : (
          <ReportView error={serverError} report={report} loadReport={this.loadReport} />
        )}
      </div>
    );
  }
}

export default withErrorHandling(withUser(Report));

function getFormattedNowDate() {
  return format(new Date(), BACKEND_DATE_FORMAT);
}
