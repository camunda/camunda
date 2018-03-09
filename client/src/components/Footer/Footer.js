import React from 'react';

import './Footer.css';

import {getImportProgress, getConnectionStatus} from './service';

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
  };

  loadConnectionStatus = async () => {
    const {engineConnections, connectedToElasticsearch} = await getConnectionStatus();
    this.setState({engineConnections, connectedToElasticsearch});
  };

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

    const importFinished = importProgress !== null && importProgress === 100;

    let connectionFragment = '';

    if (engineConnections !== null) {
      connectionFragment = (
        <ul className="Footer__connect-status">
          {Object.keys(engineConnections).map(key => {
            return (
              <li
                key={key}
                className={
                  'Footer__connect-status-item' +
                  (engineConnections[key]
                    ? importFinished ? ' is-connected' : ' is-in-progress'
                    : '')
                }
                title={
                  engineConnections[key]
                    ? importFinished ? key + ' is connected' : 'Import progress: ' + importProgress
                    : key + ' is not connected'
                }
              >
                {key}
              </li>
            );
          })}
          <li
            className={
              'Footer__connect-status-item' +
              (connectedToElasticsearch
                ? importFinished ? ' is-connected' : ' is-in-progress'
                : '')
            }
            title={
              connectedToElasticsearch
                ? importFinished
                  ? 'Elasticsearch is connected'
                  : 'Import progress: ' + importProgress
                : 'Elasticsearch is not connected'
            }
          >
            Elasticsearch
          </li>
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
