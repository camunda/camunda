/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';
import {Server} from 'mock-socket';

import ConnectionStatus from './ConnectionStatus';

let server;
beforeEach(() => {
  server = new Server('ws://localhost/ws/status');
});
afterEach(() => {
  server.stop();
});

it('displays the loading indicator if is importing', () => {
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

  const node = shallow(<ConnectionStatus />);

  runLastEffect();

  server.on('connection', (server) => {
    server.send(JSON.stringify(data));
  });

  jest.runOnlyPendingTimers();

  expect(node.find('.is-in-progress')).toExist();
});

it('does not display the loading indicator if is not importing', () => {
  jest.useFakeTimers();
  const data = {
    engineStatus: {
      property1: {
        isConnected: true,
        isImporting: false,
      },
    },
    connectedToElasticsearch: true,
  };

  const node = shallow(<ConnectionStatus />);

  runLastEffect();

  server.on('connection', (server) => {
    server.send(JSON.stringify(data));
  });

  jest.runOnlyPendingTimers();

  expect(node.find('.is-in-progress')).not.toExist();
});

it('displays the connection status', () => {
  jest.useFakeTimers();
  const data = {
    engineStatus: {},
  };

  const node = shallow(<ConnectionStatus />);

  runLastEffect();

  server.on('connection', (server) => {
    server.send(JSON.stringify(data));
  });

  jest.runOnlyPendingTimers();

  expect(node.find('.status')).toExist();
});

it('should not display connection status before receiving data', () => {
  const node = shallow(<ConnectionStatus />);

  runLastEffect();

  expect(node.find('.statusItem')).not.toExist();
});

it('should display an error message when the websocket connection goes wrong', () => {
  jest.useFakeTimers();
  const node = shallow(<ConnectionStatus />);

  runLastEffect();

  server.emit('error', 'error happened');

  jest.runOnlyPendingTimers();

  expect(node.find('.error')).toExist();
});
