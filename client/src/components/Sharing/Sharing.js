import React from 'react';

import './Sharing.css';
import {ReportView} from 'components';
import {getReportData} from './service';


export default class Sharing extends React.Component {
  constructor(props) {
    super(props);

    this.id = props.match.params.id;

    this.state = {
      reportResult: null,
      loaded: false
    };

    this.loadReport();
  }

  loadReport = async () => {

    const reportResult = await getReportData(this.id);

    this.setState({
      reportResult,
      loaded: true
    });
  }

  render() {
    const {loaded, reportResult} = this.state;
    if(!loaded) {
      return <div className='Sharing__loading-indicator'>loading...</div>;
    }

    if(!reportResult) {
      return <div className='Sharing__error-message'>The resource you want to access is not available!</div>;
    }

    return (<div style={{flexDirection: 'column', display: 'flex', flexGrow: 1}}>
    <div className='Sharing__header'>
          <div className='Sharing__title-container'>
            <h1 className='Sharing__tilte'>{reportResult.report.name}</h1>
          </div>
        </div>
      <div className='Sharing__content' >
          <ReportView report={reportResult.report} />
     </div>
    </div>);
  }
}