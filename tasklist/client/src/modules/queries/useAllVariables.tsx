/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  QueryKey,
  useQuery,
  useQueryClient,
  UseQueryOptions,
} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, RequestError} from 'modules/request';
import {FullVariable, Task, Variable} from 'modules/types';
import {useState} from 'react';

type Param = {
  taskId: Task['id'];
};

type Options = Pick<
  UseQueryOptions<Variable[], RequestError | Error>,
  'enabled' | 'refetchOnWindowFocus' | 'refetchOnReconnect'
>;

function useFetchFullVariable(variablesQueryKey: QueryKey) {
  const client = useQueryClient();
  const [requestedVariables, setRequestedVariables] = useState<
    Variable['id'][]
  >([]);

  async function fetch(id: Variable['id']) {
    setRequestedVariables((variables) => [...variables, id]);
    const fullVariable = await client.fetchQuery<
      Pick<FullVariable, 'id' | 'name' | 'value'>,
      RequestError | Error
    >({
      queryKey: ['variable', id],
      queryFn: async () => {
        const {response, error} = await request(api.getFullVariable(id));

        if (response !== null) {
          return response.json();
        }

        throw error;
      },
    });

    setRequestedVariables((variables) =>
      variables.filter((variable) => variable !== id),
    );

    client.setQueryData<Variable[]>(variablesQueryKey, (cachedVariables) =>
      cachedVariables?.map((variable) => {
        if (variable.id === id) {
          return {
            ...variable,
            ...fullVariable,
            isValueTruncated: false,
          } satisfies FullVariable;
        }

        return variable;
      }),
    );
  }

  return {
    fetch,
    variablesLoading: requestedVariables,
  };
}

function useAllVariables(params: Param, options: Options = {}) {
  const {taskId} = params;
  const variablesQueryKey = ['variables', taskId];
  const queryResult = useQuery<Variable[], RequestError | Error>({
    ...options,
    queryKey: variablesQueryKey,
    queryFn: async () => {
      const {response, error} = await request(api.getAllVariables({taskId}));

      if (response !== null) {
        return response.json();
      }

      throw error;
    },
  });
  const {
    fetch: fetchFullVariable,
    variablesLoading: variablesLoadingFullValue,
  } = useFetchFullVariable(variablesQueryKey);

  return {
    ...queryResult,
    fetchFullVariable,
    variablesLoadingFullValue,
  };
}

export {useAllVariables};
