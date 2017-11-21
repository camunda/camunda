import React from 'react';
import './Footer.css';

import {getImportProgress, getConnectionStatus} from './service';


export default class Footer extends React.Component {

  constructor(props) {
    super(props);

    this.state = {
      connectionStatus: null,
      importProgress: null,
      titleString: ""
    };
    this.loadConnectionStatus();
    this.loadImportProgress();
  }

  loadImportProgress = async () => {
    let response = await getImportProgress();
    let importProgress = response.progress;

    this.setState({importProgress});
  }

  loadConnectionStatus = async () => {
    const response = await getConnectionStatus();

    let connectionStatus = true;
    let titleString = "";

    Object.keys(response.engineConnections).forEach((v) => {
      if (response.engineConnections[v] === false){
        connectionStatus = false;
        titleString += `Missing connection to ${v}\n`;
      } else {
        titleString += `Connected to ${v}\n`;
      }
    });

    if(!response.connectedToElasticsearch) {
      connectionStatus = false;
      titleString += "Missing connection to Elasticsearch"
    } else {
      titleString += `Connected to Elasticsearch`;
    }

    (connectionStatus) ? connectionStatus = "✅" : connectionStatus = "❌";

    this.setState({connectionStatus, titleString});
  }

  componentDidMount() {
    setInterval(() => {
      this.loadImportProgress();
      this.loadConnectionStatus();
    }, 5000);
  }



  render() {
    return (
      <footer className='Footer'>
        <span className='import-progress-footer'>Import progress is {this.state.importProgress}%  </span>
        <span className='connection-status-footer' title={this.state.titleString}>{this.state.connectionStatus}  </span>
        © Camunda Services GmbH 2017, All Rights Reserved. | {this.props.version}
      </footer>
    );
  }
}

