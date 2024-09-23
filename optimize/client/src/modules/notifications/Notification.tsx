/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useRef, useCallback, ReactNode} from 'react';
import classnames from 'classnames';
import {ActionableNotification} from '@carbon/react';

import './Notification.scss';

export interface Config {
  id?: string;
  type?: 'success' | 'warning' | 'error' | 'info';
  text: ReactNode;
  stayOpen?: boolean;
  duration?: number;
}

interface NotificationProps {
  config: Config;
  remove: () => void;
}

export default function Notification({config, remove}: NotificationProps): JSX.Element {
  const [closing, setClosing] = useState(false);
  const closeTrigger = useRef<ReturnType<typeof setTimeout>>();
  const {text, duration, stayOpen, type} = config;
  const title = typeof text === 'string' ? text : '';
  const children = typeof text === 'string' ? undefined : text;

  const close = useCallback(() => {
    setClosing(true);
    setTimeout(remove, 350);
  }, [remove]);

  useEffect(() => {
    if (!stayOpen) {
      closeTrigger.current = setTimeout(close, duration || 4350);
    }
    return () => {
      clearTimeout(closeTrigger.current);
    };
  }, [duration, stayOpen, closeTrigger, close]);

  const keepOpen = () => {
    clearTimeout(closeTrigger.current);
  };

  return (
    <ActionableNotification
      className={classnames('Notification', {closing})}
      kind={type}
      title={title}
      onClose={() => {
        close();
        return false;
      }}
      onClick={keepOpen}
    >
      {children}
    </ActionableNotification>
  );
}
