import React from 'react';
import {StatusBar} from 'components';

import './Footer.css';

import {getImportProgress, getConnectionStatus} from './service';


export default class Footer extends React.Component {

  constructor(props) {
    super(props);

    this.state = {
      engineConnections: null,
      importProgress: null,
      titleString: ""
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
    const response = await getConnectionStatus();
    this.setState({engineConnections: response.engineConnections, connectedToElasticsearch: response.connectedToElasticsearch});
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
    let statusFragment = '';

    if(this.state.importProgress !== null && this.state.importProgress < 100) {
      statusFragment = (
        <div className='Footer__import-status'>
          <StatusBar height='6px' status={this.state.importProgress} title='Import status'/>
        </div>
      );
    }

    let connectionFragment = '';
    
    if(this.state.engineConnections !== null) {
      connectionFragment = (
        <ul className='Footer__connect-status'>
          {Object.keys(this.state.engineConnections).map((key) => {
            return (<li key={key} className={'Footer__connect-status-item' + (this.state.engineConnections[key] ? ' is-connected' : '')} title={key + (this.state.engineConnections[key] ? ' is connected' : ' is not connected')} >{key}</li>)
          })}
          <li className={'Footer__connect-status-item' + (this.state.connectedToElasticsearch ? ' is-connected' : '')} title={'Elasticsearch ' + (this.state.connectedToElasticsearch ? 'is connected' : 'is not connected')}>Elasticsearch</li>
        </ul>
      );

    }

    return (
      <footer className='Footer'>

        {statusFragment}

        <div className='Footer__content'>

          {connectionFragment}

          <div className='Footer__colophon'>
            © Camunda Services GmbH 2017, All Rights Reserved. | {this.props.version}
          </div>

        </div>

      </footer>
    );
  }
}
