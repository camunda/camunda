import { useCallback, useEffect, useState } from "react";
import { ApiDefinition, ApiPromise } from "../request";
import useApiCall, { NamedErrors } from "./useApiCall";

export interface UseApiResult<R, P = undefined> {
  data: R | null;
  loading: boolean;
  errors: string[] | null;
  namedErrors: NamedErrors<P>;
  status: number | null;
  success: boolean;
  reload(): ApiPromise<R>;
  reset(): void;
}

export type UseApi = {
  <R, P>(
    apiDefinition: ApiDefinition<R, P>,
    params: P,
    paramsValid?: boolean,
  ): UseApiResult<R, P>;
  <R>(apiDefinition: ApiDefinition<R>): UseApiResult<R>;
};

const useApi: UseApi = <R, P>(
  apiDefinition: ApiDefinition<R, P>,
  params?: P,
  paramsValid = true,
): UseApiResult<R> => {
  const [call, { data, status, errors, namedErrors, loading, success }, reset] =
    useApiCall<R, P>(apiDefinition);
  const [called, setCalled] = useState(false);

  const paramsDependency = JSON.stringify(params);
  // params are passed to dependencies as json string which is not recognised by eslint
  const reload = useCallback(
    () => call(params as P),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [call, paramsDependency],
  );

  useEffect(() => {
    if (paramsValid) {
      (async () => {
        await reload();
        setCalled(true);
      })();
    }
  }, [reload, setCalled, paramsValid]);

  useEffect(() => {
    if (!paramsValid) {
      reset();
    }
  }, [reset, paramsValid]);

  return {
    data,
    // set loading to true on initial call to prevent flickering
    loading: called ? loading : true,
    errors,
    namedErrors,
    status,
    reload,
    success,
    reset,
  };
};

export default useApi;
