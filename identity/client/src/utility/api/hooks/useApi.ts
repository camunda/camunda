import { useCallback, useEffect, useState } from "react";
import { ApiDefinition, ApiPromise, ErrorResponse } from "../request";
import useApiCall, { UseApiCallOptions } from "./useApiCall";

export interface UseApiResult<R> {
  data: R | null;
  loading: boolean;
  error: ErrorResponse | null;
  status: number | null;
  success: boolean;
  reload(): ApiPromise<R>;
  reset(): void;
}

type UseApiOptions = UseApiCallOptions & {
  paramsValid?: boolean;
};

export type UseApi = {
  <R, P>(
    apiDefinition: ApiDefinition<R, P>,
    params: P,
    Options?: UseApiOptions,
  ): UseApiResult<R>;
  <R>(apiDefinition: ApiDefinition<R>): UseApiResult<R>;
};

const useApi: UseApi = <R, P>(
  apiDefinition: ApiDefinition<R, P>,
  params?: P,
  Options: UseApiOptions = {
    paramsValid: true,
    suppressErrorNotification: false,
  },
): UseApiResult<R> => {
  const [call, { data, status, error, loading, success }, reset] = useApiCall<
    R,
    P
  >(apiDefinition, Options);
  const [called, setCalled] = useState(false);

  const paramsDependency = JSON.stringify(params);
  // params are passed to dependencies as json string which is not recognised by eslint
  const reload = useCallback(
    () => call(params as P),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [call, paramsDependency],
  );

  useEffect(() => {
    if (Options.paramsValid) {
      (async () => {
        await reload();
        setCalled(true);
      })();
    }
  }, [reload, setCalled, Options.paramsValid]);

  useEffect(() => {
    if (!Options.paramsValid) {
      reset();
    }
  }, [reset, Options.paramsValid]);

  return {
    data,
    // set loading to true on initial call to prevent flickering
    loading: called ? loading : true,
    error,
    status,
    reload,
    success,
    reset,
  };
};

export default useApi;
