/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import {withErrorHandling, withUser} from 'HOC';
import {ErrorPage, LoadingIndicator} from 'components';
import {evaluateReport} from 'services';

import ReportEdit from './ReportEdit';
import ReportView from './ReportView';

import newReport from './newReport.json';

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
    const user = await this.props.getUser();
    const now = getFormattedNowDate();
    this.setState({
      report: {
        ...newReport[this.getId()],
        name: t('report.new'),
        lastModified: now,
        created: now,
        lastModifier: user.id,
      },
    });
  };

  loadReport = () => {
    this.props.mightFail(
      evaluateReport(this.getId()),
      async (response) => {
        this.setState({
          report: response,
        });
      },
      async (e) => {
        const report = (await e.json()).reportDefinition;
        if (report) {
          this.setState({report});
        } else {
          this.setState({
            serverError: e.status,
          });
        }
        return;
      }
    );
  };

  render() {
    const {report, serverError} = this.state;

    if (serverError) {
      return <ErrorPage />;
    }

    if (!report) {
      return <LoadingIndicator />;
    }

    const {viewMode} = this.props.match.params;

    return (
      <div className="Report-container">
        {viewMode === 'edit' ? (
          <ReportEdit
            isNew={this.isNew()}
            updateOverview={async (newReport) => {
              const user = await this.props.getUser();
              this.setState({
                report: {
                  ...newReport,
                  lastModified: getFormattedNowDate(),
                  lastModifier: user.id,
                },
              });
            }}
            report={report}
          />
        ) : (
          <ReportView report={report} />
        )}
      </div>
    );
  }
}

export default withErrorHandling(withUser(Report));

function getFormattedNowDate() {
  return moment().format('Y-MM-DDTHH:mm:ss.SSSZZ');
}
