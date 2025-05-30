/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen} from '@testing-library/react';
import {CopyVariablesButton} from './CopyVariablesButton';
import {render} from 'modules/testing-library';
import {createVariableV2} from 'modules/testUtils';
import {VariablesContext} from 'App/ProcessInstance/BottomPanel/VariablePanel/v2/VariablesContext';

describe('CopyVariableButton', () => {
  interface WrapperProps {
    children?: React.ReactNode;
  }

  interface GetWrapperProps {
    value?: React.ContextType<typeof VariablesContext>;
  }

  const getWrapper = (props: GetWrapperProps) => {
    const Wrapper: React.FC<WrapperProps> = ({children}) => {
      return (
        <VariablesContext.Provider
          value={
            props.value ?? {
              fetchNextPage: vi.fn(),
              hasNextPage: false,
              isFetchingNextPage: false,
              status: 'no-content',
            }
          }
        >
          {children}
        </VariablesContext.Provider>
      );
    };
    return Wrapper;
  };

  it('should be disabled (no variables)', () => {
    render(<CopyVariablesButton />, {wrapper: getWrapper({})});

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should be disabled (too many variables)', () => {
    const variables = [...Array(50)].map((_, i) => ({
      name: i.toString(),
      value: 'value',
      tenantId: 'tenantId',
      processInstanceKey: 'processInstanceKey',
      isTruncated: false,
      variableKey: 'variableKey',
      scopeKey: 'scopeKey',
    }));

    render(<CopyVariablesButton />, {
      wrapper: getWrapper({
        value: {
          variablesData: {
            pages: [
              {
                items: variables,
                page: {
                  totalItems: 50,
                },
              },
              {
                items: variables,
                page: {
                  totalItems: 50,
                },
              },
            ],
            pageParams: [],
          },
          fetchNextPage: vi.fn(),
          hasNextPage: false,
          isFetchingNextPage: false,
          status: 'variables',
        },
      }),
    });

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should be disabled (truncated values)', () => {
    render(<CopyVariablesButton />, {
      wrapper: getWrapper({
        value: {
          variablesData: {
            pages: [
              {
                items: [createVariableV2({isTruncated: true})],
                page: {
                  totalItems: 1,
                },
              },
            ],
            pageParams: [],
          },
          fetchNextPage: vi.fn(),
          hasNextPage: false,
          isFetchingNextPage: false,
          status: 'variables',
        },
      }),
    });

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should copy variables to clipboard', async () => {
    const writeTextSpy = vi.spyOn(navigator.clipboard, 'writeText');

    const {user} = render(<CopyVariablesButton />, {
      wrapper: getWrapper({
        value: {
          variablesData: {
            pages: [
              {
                items: [
                  createVariableV2({
                    name: 'jsonVariable',
                    value: JSON.stringify({a: 123, b: [1, 2, 3], c: 'text'}),
                  }),
                  createVariableV2({
                    name: 'numberVariable',
                    value: '666',
                  }),
                  createVariableV2({
                    name: 'stringVariable',
                    value: '"text"',
                  }),
                ],
                page: {
                  totalItems: 3,
                },
              },
            ],
            pageParams: [],
          },
          fetchNextPage: vi.fn(),
          hasNextPage: false,
          isFetchingNextPage: false,
          status: 'variables',
        },
      }),
    });

    expect(screen.getByRole('button')).toBeEnabled();
    await user.click(screen.getByRole('button'));

    expect(writeTextSpy).toHaveBeenCalledWith(
      '{"jsonVariable":{"a":123,"b":[1,2,3],"c":"text"},"numberVariable":666,"stringVariable":"text"}',
    );
  });
});
