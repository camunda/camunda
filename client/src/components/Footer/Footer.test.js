/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Server} from 'mock-socket';

import Footer from './Footer';
import {getOptimizeVersion} from 'config';

jest.mock('config', () => {
  return {
    getOptimizeVersion: jest.fn(),
  };
});

let server;
beforeEach(() => {
  server = new Server('ws://localhost/ws/status');
});
afterEach(() => {
  server.stop();
});

it('includes the version number retrieved from back-end', async () => {
  const version = 'alpha';
  getOptimizeVersion.mockReturnValue(version);

  const node = shallow(<Footer />);

  await flushPromises();

  expect(node.find('.colophon')).toIncludeText(version);
});

it('displays the loading indicator if is importing', () => {
  const node = shallow(<Footer />);

  node.setState({
    engineStatus: {
      property1: {
        isConnected: true,
        isImporting: true,
      },
    },
    connectedToElasticsearch: true,
    loaded: true,
  });

  expect(node.find('.is-in-progress')).toExist();
});

it('does not display the loading indicator if is not importing', () => {
  const node = shallow(<Footer />);

  node.setState({
    engineStatus: {
      property1: {
        isConnected: true,
        isImporting: false,
      },
    },
    connectedToElasticsearch: true,
    loaded: true,
  });

  expect(node.find('.is-in-progress')).not.toExist();
});

it('displays the connection status', () => {
  const node = shallow(<Footer />);

  node.setState({
    engineStatus: {
      property1: {
        isConnected: true,
        isImporting: false,
      },
    },
    loaded: true,
  });

  expect(node.find('.status')).toExist();
});

it('should not display connection status before receiving data', () => {
  const node = shallow(<Footer />);

  expect(node.find('.statusItem')).not.toExist();

  node.setState({loaded: true});

  expect(node.find('.statusItem')).toExist();
});

it('should display an error message when the websocket connection goes wrong', () => {
  const node = shallow(<Footer />);
  node.setState({loaded: true});

  expect(node.find('.statusItem')).toExist();
  expect(node.find('.error')).not.toExist();

  node.setState({error: true});

  expect(node.find('.statusItem')).not.toExist();
  expect(node.find('.error')).toExist();
});

it('should store data from the socket connection in state', () => {
  jest.useFakeTimers();
  const data = {
    engineStatus: {
      property1: {
        isConnected: true,
        isImporting: true,
      },
    },
    connectedToElasticsearch: true,
  };

  const node = shallow(<Footer />);

  server.on('connection', (server) => {
    server.send(JSON.stringify(data));
  });

  jest.runOnlyPendingTimers();

  expect(node.state().connectionStatus).toEqual(data.connectionStatus);
  expect(node.state().isImporting).toEqual(data.isImporting);
});
