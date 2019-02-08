import React from 'react';

import {withErrorHandling} from 'HOC';
import {ErrorPage, LoadingIndicator} from 'components';
import {loadSingleReport, evaluateReport} from './service';

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
        loaded: false,
        serverError: null
      };
    }

    componentDidMount = async () => {
      await this.props.mightFail(
        loadSingleReport(this.id),
        async response => {
          this.setState({
            loaded: true,
            report: (await evaluateReport(this.id)) || response
          });
        },
        error => {
          const serverError = error.status;
          this.setState({
            serverError
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
      const {loaded, serverError} = this.state;

      if (serverError) {
        return <ErrorPage entity="report" statusCode={serverError} />;
      }

      if (!loaded) {
        return <LoadingIndicator />;
      }

      const {viewMode} = this.props.match.params;
      return (
        <div className="Report-container">
          {viewMode === 'edit' ? (
            <ReportEdit
              isNew={this.isNew}
              updateOverview={report => this.setState({report})}
              report={this.state.report}
            />
          ) : (
            <ReportView report={this.state.report} />
          )}
        </div>
      );
    }
  }
);
