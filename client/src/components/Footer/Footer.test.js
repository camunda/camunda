import React from 'react';
import {shallow, mount} from 'enzyme';

import {Server} from 'mock-socket';

import Footer from './Footer';

let server;
beforeEach(() => {
  server = new Server('ws://localhost:8090/ws/status');
});
afterEach(() => {
  server.stop();
});

it('renders without crashing', () => {
  shallow(<Footer />);
});

it('includes the version number provided as property', () => {
  const version = 'alpha';

  const node = mount(<Footer version={version} />);
  expect(node).toIncludeText(version);
});

it('displays the loading indicator if import progress is less than 100', () => {
  const node = mount(<Footer />);

  node.setState({
    connectionStatus: {
      connectedToElasticsearch: true,
      engineConnections: {
        property1: true
      }
    },
    progress: {
      property1: 50
    }
  });

  expect(node.find('.is-in-progress')).toBePresent();
});

it('does not display the loading indicator if import progress is 100', () => {
  const node = mount(<Footer />);

  node.setState({
    connectionStatus: {
      connectedToElasticsearch: true,
      engineConnections: {
        property1: true
      }
    },
    progress: {
      property1: 100
    }
  });

  expect(node.find('.is-in-progress')).not.toBePresent();
});

it('displays the connection status', () => {
  const node = mount(<Footer version="2.0.0" />);

  node.setState({
    engineConnections: {
      engine1: true
    }
  });

  expect(node.find('.Footer__connect-status')).toBePresent();
});

it('should store data from the socket connection in state', () => {
  const data = {
    connectionStatus: {
      connectedToElasticsearch: true,
      engineConnections: {
        property1: true
      }
    },
    progress: {
      property1: 100
    }
  };

  const node = mount(<Footer />);

  server.send(JSON.stringify(data));

  expect(node.state()).toEqual(data);
});
