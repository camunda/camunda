/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow, mount} from 'enzyme';

import {Server} from 'mock-socket';

import Footer from './Footer';
import {getOptimizeVersion} from 'services';

jest.mock('services', () => {
  return {
    getOptimizeVersion: jest.fn()
  };
});

let server;
beforeEach(() => {
  server = new Server('ws://localhost/ws/status');
});
afterEach(() => {
  server.stop();
});

it('renders without crashing', () => {
  shallow(<Footer />);
});

it('includes the version number retrieved from back-end', async () => {
  const version = 'alpha';
  getOptimizeVersion.mockReturnValue(version);

  const node = await mount(<Footer />);
  expect(node).toIncludeText(version);
});

it('displays the loading indicator if is importing', () => {
  const node = mount(<Footer />);

  node.setState({
    connectionStatus: {
      connectedToElasticsearch: true,
      engineConnections: {
        property1: true
      }
    },
    isImporting: {
      property1: true
    }
  });

  expect(node.find('.is-in-progress')).toExist();
});

it('does not display the loading indicator if is not importing', () => {
  const node = mount(<Footer />);

  node.setState({
    connectionStatus: {
      connectedToElasticsearch: true,
      engineConnections: {
        property1: true
      }
    },
    isImporting: {
      property1: false
    }
  });

  expect(node.find('.is-in-progress')).not.toExist();
});

it('displays the connection status', () => {
  const node = mount(<Footer />);

  node.setState({
    engineConnections: {
      engine1: true
    }
  });

  expect(node.find('.Footer__connect-status')).toExist();
});

it('should store data from the socket connection in state', () => {
  const data = {
    connectionStatus: {
      connectedToElasticsearch: true,
      engineConnections: {
        property1: true
      }
    },
    isImporting: {
      property1: true
    }
  };

  const node = mount(<Footer />);

  server.send(JSON.stringify(data));

  expect(node.state().connectionStatus).toEqual(data.connectionStatus);
  expect(node.state().isImporting).toEqual(data.isImporting);
});
