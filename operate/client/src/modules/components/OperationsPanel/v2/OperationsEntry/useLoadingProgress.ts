/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useRef, useEffect} from 'react';

/**
 * Returns a (faked) progress percentage and an isComplete indicator
 * based on totalCount, completedCount and isFinished parameters.
 *
 * isFinished means the operation itself is finished
 * isComplete is true when the visual progress has finished
 */
const useLoadingProgress = ({
  totalCount,
  processedCount,
  isFinished,
}: {
  totalCount: number;
  processedCount: number;
  isFinished: boolean;
}) => {
  const [fakeProgressPercentage, setFakeProgressPercentage] = useState(0);
  const [isComplete, setIsComplete] = useState(totalCount === processedCount);
  const initialCompleteRef = useRef(totalCount === processedCount);
  const fakeTimeoutId = useRef<ReturnType<typeof setTimeout> | null>(null);
  const completedTimeoutId = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fakeStartPercentage = 10;
  const fakeProgressUpdateRate = 500;

  useEffect(() => {
    if (
      fakeProgressPercentage < 100 &&
      !initialCompleteRef.current &&
      fakeTimeoutId.current === null &&
      totalCount > 0
    ) {
      const realProgressPercentage = Math.floor(
        (100 / totalCount) * processedCount,
      );

      fakeTimeoutId.current = setTimeout(() => {
        setFakeProgressPercentage(
          Math.max(
            Math.min(fakeProgressPercentage + 1, 95),
            realProgressPercentage,
            fakeStartPercentage,
          ),
        );
        fakeTimeoutId.current = null;
      }, fakeProgressUpdateRate);
    }

    return () => {
      if (fakeTimeoutId.current !== null) {
        clearTimeout(fakeTimeoutId.current);
        fakeTimeoutId.current = null;
      }
    };
  }, [fakeProgressPercentage, totalCount, processedCount]);

  useEffect(() => {
    if (isFinished && !initialCompleteRef.current && !isComplete) {
      setFakeProgressPercentage(100);
      completedTimeoutId.current = setTimeout(() => {
        setIsComplete(true);
      }, 2000);
    }

    return () => {
      if (completedTimeoutId.current !== null) {
        clearTimeout(completedTimeoutId.current);
        completedTimeoutId.current = null;
      }
    };
  }, [isFinished, isComplete]);

  return {fakeProgressPercentage, isComplete};
};

export {useLoadingProgress};
