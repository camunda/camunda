import { useCallback, useMemo, useState } from "react";
import { ApiCall, ApiDefinition, namedErrorsReducer } from "../request";
import useTranslate from "../../localization";
import { useNotifications } from "src/components/notifications";
import { getApiBaseUrl } from "src/configuration";

export type NamedErrors<R> = Partial<Record<keyof R, string[]>> | null;

type ResetApiCall = () => void;

export type UseApiCallResult<R, P> = [
  ApiCall<R, P>,
  {
    data: R | null;
    loading: boolean;
    errors: string[] | null;
    namedErrors: NamedErrors<P>;
    status: number;
    success: boolean;
  },
  ResetApiCall,
];

export type UseApiCall = {
  <R, P>(apiDefinition: ApiDefinition<R, P>): UseApiCallResult<R, P>;
};

/**
 * This hook is used to trigger an api call in an event handler.
 * The return parameters' data, status and errors have the same value as the
 * promise result of the call function
 * @param {ApiDefinition} apiDefinition
 * @return UseApiCallResult
 */
const useApiCall: UseApiCall = <R, P>(
  apiDefinition: ApiDefinition<R, P>,
): UseApiCallResult<R, P> => {
  const { enqueueNotification } = useNotifications();
  const { t } = useTranslate("components");
  const [data, setData] = useState<R | null>(null);
  const [status, setStatus] = useState<number>(-1);
  const [errors, setErrors] = useState<string[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const namedErrors = useMemo(
    () =>
      (!loading &&
        (errors?.reduce(namedErrorsReducer, {}) as NamedErrors<P>)) ||
      null,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [JSON.stringify([errors, loading])],
  );

  const reset = () => {
    setData(null);
    setStatus(-1);
    setErrors(null);
    setLoading(false);
    setSuccess(false);
    setLoading(false);
  };

  const call: ApiCall<R, P> = useCallback(
    async (params?: P) => {
      setLoading(true);

      const {
        data: apiData,
        status: apiStatus,
        errors: apiErrors,
        success: apiSuccess,
      } = await apiDefinition(params as P)(getApiBaseUrl());

      if (apiStatus >= 400) {
        switch (apiStatus) {
          case 401:
            enqueueNotification({
              kind: "error",
              title: t("Unauthorized"),
              subtitle: t("Looks like your session has expired") ?? "",
            });
            break;
          case 403:
            enqueueNotification({
              kind: "error",
              title: t("Forbidden"),
              subtitle:
                t(
                  "You don't have access to perform this operation on given resource",
                ) ?? "",
            });
            break;
          case 404:
            enqueueNotification({
              kind: "error",
              title: t("Not found"),
              subtitle: t("The requested entity has not been found") ?? "",
            });
            break;
          default:
            enqueueNotification({
              kind: "error",
              title: t("An error occurred"),
              subtitle: t("Please try again later") ?? "",
            });
            break;
        }
      }

      setData(apiData);
      setStatus(apiStatus);
      setErrors(apiErrors);
      setLoading(false);
      setSuccess(apiSuccess);

      return {
        data: apiData,
        status: apiStatus,
        errors: apiErrors,
        success: apiSuccess,
      };
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [apiDefinition],
  ) as ApiCall<R, P>;

  return [
    call,
    {
      data,
      status,
      errors,
      loading,
      success,
      namedErrors,
    },
    reset,
  ];
};

export default useApiCall;
