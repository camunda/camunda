/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiPromise } from "src/utility/api/request";

const POLL_INTERVAL_MS = 1000;
const POLL_TIMEOUT_MS = 10_000;

type PollingStatus = "idle" | "polling" | "timeout";

type UsePollingReloadResult = {
  startPolling: () => void;
  pollingStatus: PollingStatus;
  resetPollingStatus: () => void;
  isPolling: boolean;
};

const usePollingReload = <T>(
  reload: () => ApiPromise<T>,
  compare: (current: T) => boolean,
): UsePollingReloadResult => {
  const [pollingStatus, setPollingStatus] = useState<PollingStatus>("idle");
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const stopPolling = useCallback(() => {
    if (intervalRef.current !== null) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (timeoutRef.current !== null) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  const resetPollingStatus = useCallback(() => {
    setPollingStatus("idle");
  }, []);

  const startPolling = useCallback(() => {
    stopPolling();
    setPollingStatus("polling");

    const poll = async () => {
      const result = await reload();

      if (result.success && result.data) {
        if (compare(result.data)) {
          stopPolling();
          setPollingStatus("idle");
        }
      }
    };

    intervalRef.current = setInterval(() => {
      void poll();
    }, POLL_INTERVAL_MS);

    timeoutRef.current = setTimeout(() => {
      stopPolling();
      setPollingStatus("timeout");
    }, POLL_TIMEOUT_MS);
  }, [stopPolling, reload]);

  useEffect(() => {
    return () => {
      stopPolling();
    };
  }, []);

  return {
    startPolling,
    pollingStatus,
    resetPollingStatus,
    isPolling: pollingStatus === "polling",
  };
};

export { usePollingReload };
