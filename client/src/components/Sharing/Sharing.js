import React from 'react';

import './Sharing.css';
import {ReportView, DashboardView} from 'components';
import {evaluateEntity, loadReport} from './service';


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
  }

  getType = () => {
    return this.props.match.params.type;
  }

  performEvaluation = async () => {

    const evaluationResult = await evaluateEntity(this.getId(), this.getType());

    this.setState({
      evaluationResult,
      loading: false
    });
  }

  getSharingView = () => {
    if(this.getType() === 'report') {
      return <ReportView report={this.state.evaluationResult.report}/>;
    } else {
      return <DashboardView loadReport={loadReport} reports={this.state.evaluationResult.dashboard.reportShares}/>;
    }
  }

  hasValidType(type) {
    return type === 'report' || type === 'dashboard';
  }

  render() {
    const {loading, evaluationResult} = this.state;
    if(loading) {
      return <div className='Sharing__loading-indicator'>loading...</div>;
    }

    if(!evaluationResult || !this.hasValidType(this.getType())) {
      return <div className='Sharing__error-message'>The resource you want to access is not available!</div>;
    }

    const SharingView = this.getSharingView();
    return (<div className='Sharing'>
    <div className='Sharing__header'>
          <div className='Sharing__title-container'>
            <h1 className='Sharing__tilte'>{evaluationResult[this.getType()].name}</h1>
          </div>
        </div>
      <div className='Sharing__content' >
        {SharingView}
     </div>
    </div>);
  }
}