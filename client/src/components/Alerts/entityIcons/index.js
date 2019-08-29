/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ReactComponent as alert} from './alert.svg';
import {ReactComponent as alerts} from './alerts.svg';
const icons = {
  alert: {
    header: {Component: alerts},
    generic: {Component: alert}
  }
};

export default icons;
