/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {withErrorHandling} from 'HOC';
import {ErrorPage, LoadingIndicator} from 'components';
import {evaluateReport} from 'services';

import ReportEdit from './ReportEdit';
import ReportView from './ReportView';

import './Report.scss';

export default withErrorHandling(
  class Report extends React.Component {
    constructor(props) {
      super(props);

      this.id = props.match.params.id;
      this.isNew = props.location.search === '?new';

      this.state = {
        report: undefined,
        collections: [],
        creatingCollection: false,
        serverError: null
      };
    }

    componentDidMount = async () => {
      await this.props.mightFail(
        await evaluateReport(this.id),
        async response => {
          this.setState({
            report: response
          });
        },
        ({status}) => {
          this.setState({
            serverError: status
          });
          return;
        }
      );
    };

    componentDidUpdate() {
      if (this.isNew) {
        this.isNew = false;
      }
    }

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
              isNew={this.isNew}
              updateOverview={report => this.setState({report})}
              report={report}
            />
          ) : (
            <ReportView report={report} />
          )}
        </div>
      );
    }
  }
);
