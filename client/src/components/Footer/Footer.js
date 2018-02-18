import React from 'react';
import {ProgressBar} from 'components';

import './Footer.css';

import {getImportProgress, getConnectionStatus} from './service';

import {getToken} from 'credentials';


export default class Footer extends React.Component {

  constructor(props) {
    super(props);

    this.state = {
      engineConnections: null,
      importProgress: null,
      connectedToElasticsearch: null
    };
    this.loadConnectionStatus();
    this.loadImportProgress();
  }

  loadImportProgress = async () => {
    const response = await getImportProgress();
    const importProgress = response.progress;
    this.setState({importProgress});
  }

  loadConnectionStatus = async () => {
    const {engineConnections, connectedToElasticsearch} = await getConnectionStatus();
    this.setState({engineConnections, connectedToElasticsearch});
  }

  componentDidMount() {
    this.refreshIntervalHandle = setInterval(() => {
      this.loadImportProgress();
      this.loadConnectionStatus();
    }, 5000);
  }

  componentWillUnmount() {
    clearInterval(this.refreshIntervalHandle);
  }

  render() {
    const {importProgress, engineConnections, connectedToElasticsearch} = this.state;
    
    let statusFragment = '';

    if(importProgress !== null && importProgress < 100) {
      statusFragment = (
        <div className='Footer__import-status'>
          <ProgressBar height='6px' status={importProgress} title='Import status'/>
        </div>
      );
    }
    
    let connectionFragment = '';
    
    if(engineConnections !== null) {
      connectionFragment = (
        <ul className='Footer__connect-status'>
          {Object.keys(engineConnections).map((key) => {
            return (<li key={key} className={'Footer__connect-status-item' + (engineConnections[key] ? ' is-connected' : '')} title={key + (engineConnections[key] ? ' is connected' : ' is not connected')} >{key}</li>)
          })}
          <li className={'Footer__connect-status-item' + (connectedToElasticsearch ? ' is-connected' : '')} title={'Elasticsearch ' + (connectedToElasticsearch ? 'is connected' : 'is not connected')}>Elasticsearch</li>
        </ul>
      );
    }
    

    return ( getToken() ?
      <footer className='Footer'>
        {statusFragment}
        <div className='Footer__content'>
          {connectionFragment}
          <div className='Footer__colophon'>
            © Camunda Services GmbH 2017, All Rights Reserved. | {this.props.version}
          </div>
        </div>
      </footer> : ''
    );
  }
}
