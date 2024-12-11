/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shouldDisplayInfoNotification} from './shouldDisplayInfoNotification';

describe('shouldDisplayInfoNotification', () => {
  it('should display info notification', () => {
    expect(
      shouldDisplayInfoNotification(
        'io.camunda.zeebe.client.api.command.ClientException: java.net.SocketTimeoutException: 10 SECONDS',
      ),
    ).toBe(true);
  });

  it('should not display info notification', () => {
    expect(
      shouldDisplayInfoNotification(
        'io.camunda.zeebe.client.api.command.ClientException',
      ),
    ).toBe(false);
    expect(shouldDisplayInfoNotification('foo')).toBe(false);
    expect(shouldDisplayInfoNotification('')).toBe(false);
  });
});
