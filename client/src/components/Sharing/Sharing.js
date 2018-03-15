import React from 'react';

import './Sharing.css';
import {ReportView, DashboardView, Icon, Button} from 'components';
import {evaluateEntity, createLoadReportCallback} from './service';

export default class Sharing extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      evaluationResult: null,
      loading: true
    };

    this.performEvaluation();
  }

  getId = () => {
    return this.props.match.params.id;
  };

  getType = () => {
    return this.props.match.params.type;
  };

  performEvaluation = async () => {
    const evaluationResult = await evaluateEntity(this.getId(), this.getType());

    this.setState({
      evaluationResult,
      loading: false
    });
  };

  getSharingView = () => {
    if (this.getType() === 'report') {
      return <ReportView report={this.state.evaluationResult} />;
    } else {
      return (
        <DashboardView
          loadReport={createLoadReportCallback(this.getId())}
          reports={this.state.evaluationResult.reports}
          disableNameLink
        />
      );
    }
  };

  hasValidType(type) {
    return type === 'report' || type === 'dashboard';
  }

  openViewMode = () => {
    window.open(`/${this.getType()}/${this.state.evaluationResult.id}`, '_blank');
  };

  render() {
    const {loading, evaluationResult} = this.state;
    if (loading) {
      return <div className="Sharing__loading-indicator">loading...</div>;
    }

    if (!evaluationResult || !this.hasValidType(this.getType())) {
      return (
        <div className="Sharing__error-message">
          The resource you want to access is not available!
        </div>
      );
    }

    const SharingView = this.getSharingView();
    return (
      <div className="Sharing">
        <div className="Sharing__header">
          <div className="Sharing__title-container">
            <h1 className="Sharing__tilte">{evaluationResult.name}</h1>
            <Button onClick={this.openViewMode} className="Sharing__title-button">
              <Icon type="share" renderedIn="span" />
              <span>Open in Optimize</span>
            </Button>
          </div>
        </div>
        <div className="Sharing__content">{SharingView}</div>
      </div>
    );
  }
}
