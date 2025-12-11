/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Restricted} from '../index';
import {render, screen} from 'modules/testing-library';

describe('Restricted', () => {
  beforeEach(() => {
    vi.stubGlobal('clientConfig', {
      resourcePermissionsEnabled: true,
    });
  });

  it('should show restricted content if user has write permissions and no restricted resource based scopes defined', async () => {
    const {rerender} = render(
      <Restricted>
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: [],
          permissions: ['UPDATE_PROCESS_INSTANCE'],
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render restricted content when resource based permissions are disabled', async () => {
    vi.stubGlobal('clientConfig', {
      resourcePermissionsEnabled: false,
    });

    render(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: ['UPDATE_PROCESS_INSTANCE'],
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();
  });

  it('should render restricted content', async () => {
    const {rerender} = render(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['UPDATE_PROCESS_INSTANCE'],
          permissions: ['UPDATE_PROCESS_INSTANCE'],
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: ['UPDATE_PROCESS_INSTANCE'],
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE'],
          permissions: ['DELETE'],
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE_PROCESS_INSTANCE'],
          permissions: ['DELETE_PROCESS_INSTANCE'],
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.getByText('test content')).toBeInTheDocument();

    rerender(
      <Restricted
        resourceBasedRestrictions={{
          scopes: ['DELETE_PROCESS_INSTANCE'],
          permissions: [],
        }}
      >
        <div>test content</div>
      </Restricted>,
    );

    expect(screen.queryByText('test content')).not.toBeInTheDocument();
  });
});
