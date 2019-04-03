/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import 'raf/polyfill';
import Enzyme from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';
import 'jest-enzyme';
import {shim as objectValuesShim} from 'object.values';
import 'element-closest';

Enzyme.configure({adapter: new Adapter()});

document.execCommand = jest.fn();

global.MutationObserver = class MutationObserver {
  observe() {}
};

objectValuesShim();
