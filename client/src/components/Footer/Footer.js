import React from 'react';

import './Footer.css';

export default class Footer extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      connectionStatus: {
        engineConnections: {},
        connectedToElasticsearch: true // initial status before we get first data
      },
      isImporting: {}
    };
  }

  componentDidMount() {
    this.connection = new WebSocket('ws://localhost:8090/ws/status');

    this.connection.addEventListener('message', ({data}) => {
      this.setState(JSON.parse(data));
    });
  }

  componentWillUnmount() {
    this.connection.close();
  }

  renderListElement = (key, connectionStatus, isImporting) => {
    let className = 'Footer__connect-status-item';
    let title;

    if (connectionStatus) {
      if (isImporting) {
        title = key + ' is importing ... ';
        className += ' is-in-progress';
      } else {
        className += ' is-connected';
        title = key + ' is connected';
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
    const {
      isImporting,
      connectionStatus: {engineConnections, connectedToElasticsearch}
    } = this.state;

    return (
      <footer className="Footer">
        <div className="Footer__content">
          <ul className="Footer__connect-status">
            {Object.keys(engineConnections).map(key => {
              return this.renderListElement(key, engineConnections[key], isImporting[key]);
            })}
            {this.renderListElement('Elasticsearch', connectedToElasticsearch, false)}
          </ul>
          <div className="Footer__colophon">
            © Camunda Services GmbH 2017, All Rights Reserved. | {this.props.version}
          </div>
        </div>
      </footer>
    );
  }
}
