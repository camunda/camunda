/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Restricted} from './index';
import {render, screen} from '@testing-library/react';
import {authenticationStore} from 'modules/stores/authentication';

describe('Restricted', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should not render content that user has no permission for', () => {
    authenticationStore.setRoles(['view']);
    render(
      <Restricted scopes={['edit']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });

  it('should render content that user has permission for 2', () => {
    authenticationStore.setRoles(['view']);
    render(
      <Restricted scopes={['view', 'edit']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content that user has permission for', () => {
    authenticationStore.setRoles(['view', 'edit']);
    render(
      <Restricted scopes={['edit']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render content by default (user has no roles defined)', () => {
    authenticationStore.setRoles(undefined);
    render(
      <Restricted scopes={['edit']}>
        <div>test content</div>
      </Restricted>
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });
});
