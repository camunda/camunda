/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {shim as objectValuesShim} from 'object.values';
import {shim as arrayIncludesShim} from 'array-includes';
import {shim as arrayFindShim} from 'array.prototype.find';
import 'string.prototype.includes';
import 'element-closest';

import './array_flat';
import './array_findIndex';
import './number_isNaN';
import './number_epsilon';
import './nodeList_forEach';
import './array_from';
import './object_entries';

if (!Object.values) {
  objectValuesShim();
}

arrayIncludesShim();
arrayFindShim();
