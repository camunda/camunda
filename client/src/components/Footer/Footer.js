import React from 'react';
import Async from 'Async';

import './Footer.css';

import {getImportProgress, getConnectionStatus} from './service';

export default class Footer extends Async.React.Component {
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
    this.await(getImportProgress(), response => {
      const importProgress = response.progress;
      this.setState({importProgress});
    });
  };

  loadConnectionStatus = () => {
    this.await(getConnectionStatus(), ({engineConnections, connectedToElasticsearch}) => {
      this.setState({engineConnections, connectedToElasticsearch});
    });
  };

  componentDidMount() {
    this.refreshIntervalHandle = setInterval(() => {
      this.loadImportProgress();
      this.loadConnectionStatus();
    }, 5000);
  }

  componentWillUnmount() {
    clearInterval(this.refreshIntervalHandle);
    this.cancelAwait();
  }

  renderListElement = (key, connectionStatus, importProgress) => {
    const importFinished = importProgress !== null && importProgress === 100;
    let className = 'Footer__connect-status-item';
    let title;

    if (connectionStatus) {
      if (importFinished) {
        className += ' is-connected';
        title = key + ' is connected';
      } else {
        title = 'Import progress is ' + importProgress + '%';
        className += ' is-in-progress';
      }
    } else {
      title = key + ' is not connected';
    }

    return (
      <li key={key} className={className} title={title}>
        {key}
      </li>
    );
  };

  render() {
    const {importProgress, engineConnections, connectedToElasticsearch} = this.state;

    let connectionFragment = '';

    if (engineConnections !== null) {
      connectionFragment = (
        <ul className="Footer__connect-status">
          {Object.keys(engineConnections).map(key => {
            return this.renderListElement(key, engineConnections[key], importProgress);
          })}
          {this.renderListElement('Elasticsearch', connectedToElasticsearch, importProgress)}
        </ul>
      );
    }

    return (
      <footer className="Footer">
        <div className="Footer__content">
          {connectionFragment}
          <div className="Footer__colophon">
            © Camunda Services GmbH 2017, All Rights Reserved. | {this.props.version}
          </div>
        </div>
      </footer>
    );
  }
}
