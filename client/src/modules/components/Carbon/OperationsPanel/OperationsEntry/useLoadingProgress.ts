/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useRef, useEffect} from 'react';

const useLoadingProgress = ({
  totalCount,
  finishedCount,
}: {
  totalCount: number;
  finishedCount: number;
}) => {
  const [fakeProgressPercentage, setFakeProgressPercentage] = useState(0);
  const [isComplete, setIsComplete] = useState(totalCount === finishedCount);
  const initialCompleteRef = useRef(totalCount === finishedCount);
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
        (100 / totalCount) * finishedCount
      );

      fakeTimeoutId.current = setTimeout(() => {
        setFakeProgressPercentage(
          Math.max(
            Math.min(fakeProgressPercentage + 1, 95),
            realProgressPercentage,
            fakeStartPercentage
          )
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
  }, [fakeProgressPercentage, totalCount, finishedCount]);

  useEffect(() => {
    if (
      totalCount === finishedCount &&
      !initialCompleteRef.current &&
      !isComplete
    ) {
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
  }, [totalCount, finishedCount, isComplete]);

  return {fakeProgressPercentage, isComplete};
};

export {useLoadingProgress};
