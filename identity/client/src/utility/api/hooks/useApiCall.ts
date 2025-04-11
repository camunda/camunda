/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useState } from "react";
import { ApiCall, ApiDefinition, ErrorResponse } from "../request";
import useTranslate from "../../localization";
import { useNotifications } from "src/components/notifications";
import { getApiBaseUrl } from "src/configuration";

type ResetApiCall = () => void;

export type UseApiCallResult<R, P> = [
  ApiCall<R, P>,
  {
    data: R | null;
    loading: boolean;
    error: ErrorResponse<"detailed"> | null;
    status: number;
    success: boolean;
  },
  ResetApiCall,
];

export type UseApiCallOptions = {
  suppressErrorNotification?: boolean;
};

export type UseApiCall = {
  <R, P>(
    apiDefinition: ApiDefinition<R, P>,
    options?: UseApiCallOptions,
  ): UseApiCallResult<R, P>;
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
  options: UseApiCallOptions = { suppressErrorNotification: false },
): UseApiCallResult<R, P> => {
  const { enqueueNotification } = useNotifications();
  const { t } = useTranslate("components");
  const [data, setData] = useState<R | null>(null);
  const [status, setStatus] = useState<number>(-1);
  const [error, setError] = useState<ErrorResponse<"detailed"> | null>(null);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const reset = () => {
    setData(null);
    setStatus(-1);
    setError(null);
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
        error: apiError,
        success: apiSuccess,
      } = await apiDefinition(params as P)(getApiBaseUrl());

      if (apiStatus >= 400 && !options.suppressErrorNotification) {
        switch (apiStatus) {
          case 401:
            enqueueNotification({
              kind: "error",
              title: t("unauthorized"),
              subtitle: t("sessionExpired"),
            });
            break;
          case 403:
            enqueueNotification({
              kind: "error",
              title: t("forbidden"),
              subtitle: t("accessDenied"),
            });
            break;
          case 404:
            enqueueNotification({
              kind: "error",
              title: t("notFound"),
              subtitle: t("entityNotFound"),
            });
            break;
          default:
            if (apiError && isDetailedError(apiError)) {
              enqueueNotification({
                kind: "error",
                title: apiError.title,
                subtitle: apiError.detail,
              });
            } else {
              enqueueNotification({
                kind: "error",
                title: t("errorOccurred"),
                subtitle: apiError?.error || t("tryAgainLater"),
              });
            }
            break;
        }
      }

      setData(apiData);
      setStatus(apiStatus);
      setError(apiError && isDetailedError(apiError) ? apiError : null);
      setLoading(false);
      setSuccess(apiSuccess);

      return {
        data: apiData,
        status: apiStatus,
        error: apiError,
        success: apiSuccess,
      };
    },
    [apiDefinition],
  ) as ApiCall<R, P>;

  return [
    call,
    {
      data,
      status,
      error,
      loading,
      success,
    },
    reset,
  ];
};

function isDetailedError(
  error: ErrorResponse,
): error is ErrorResponse<"detailed"> {
  return (
    typeof error === "object" &&
    "detail" in error &&
    "instance" in error &&
    "title" in error &&
    "type" in error
  );
}

export default useApiCall;
