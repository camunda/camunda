/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const clientConfigMock = `window.clientConfig = ${JSON.stringify({
  isEnterprise: false,
  canLogout: true,
  contextPath: '',
  baseName: '',
  organizationId: null,
  clusterId: null,
  mixpanelAPIHost: null,
  mixpanelToken: null,
  isLoginDelegated: false,
  tasklistUrl: null,
  resourcePermissionsEnabled: false,
  multiTenancyEnabled: false,
})};`;

export {clientConfigMock};
