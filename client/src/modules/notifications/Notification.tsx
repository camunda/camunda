/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect, useRef, useCallback} from 'react';
import classnames from 'classnames';
import {Button, Icon} from 'components';

import './Notification.scss';

export interface Config {
  id?: string;
  type?: string;
  text: string;
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

  const getIconType = (): string | null => {
    switch (config.type) {
      case 'success':
        return 'check-large';
      case 'warning':
        return 'warning';
      case 'error':
        return 'error';
      case 'hint':
        return 'hint';
      default:
        return null;
    }
  };

  const close = useCallback(() => {
    setClosing(true);
    setTimeout(remove, 350);
  }, [remove]);

  useEffect(() => {
    if (!config.stayOpen) {
      closeTrigger.current = setTimeout(close, config.duration || 4350);
    }
    return () => {
      clearTimeout(closeTrigger.current);
    };
  }, [config, closeTrigger, close]);

  const keepOpen = () => {
    clearTimeout(closeTrigger.current);
  };

  const iconType = getIconType();

  return (
    <div className={classnames('Notification', config.type, {closing})} onClick={keepOpen}>
      {iconType && <Icon type={iconType} />}
      {config.text}
      <Button className="close" onClick={close}>
        <Icon type="close-large" />
      </Button>
    </div>
  );
}
