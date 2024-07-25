/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';

import {getOptimizeVersion, getOptimizeProfile} from 'config';
import {t} from 'translation';
import {Tooltip} from 'components';

import ConnectionStatus from './ConnectionStatus';

import './Footer.scss';

export default function Footer() {
  const [optimizeVersion, setOptimizeVersion] = useState<string | null>(null);
  const [optimizeProfile, setOptimizeProfile] = useState<string | null>(null);

  useEffect(() => {
    async function fetchData() {
      const version = await getOptimizeVersion();
      const profile = await getOptimizeProfile();
      setOptimizeVersion(version);
      setOptimizeProfile(profile);
    }

    fetchData();
  }, []);

  const timezoneInfo =
    t('footer.timezone') + ' ' + Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <footer className="Footer">
      {optimizeProfile === 'platform' && <ConnectionStatus />}
      <Tooltip content={timezoneInfo} overflowOnly>
        <div className="timezone">{timezoneInfo}</div>
      </Tooltip>
      <div className="colophon">
        © Camunda Services GmbH {new Date().getFullYear()}, {t('footer.rightsReserved')} |{' '}
        {optimizeVersion}
      </div>
    </footer>
  );
}
