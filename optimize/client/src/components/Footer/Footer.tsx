/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';
import {useUiConfig} from 'hooks';

import './Footer.scss';

export default function Footer() {
  const {optimizeVersion} = useUiConfig();

  const timezoneInfo =
    t('footer.timezone') + ' ' + Intl.DateTimeFormat().resolvedOptions().timeZone;

  return (
    <footer className="Footer">
      <div title={timezoneInfo} className="timezone">
        {timezoneInfo}
      </div>
      <div className="colophon">
        © Camunda Services GmbH {new Date().getFullYear()}, {t('footer.rightsReserved')} |{' '}
        {optimizeVersion}
      </div>
    </footer>
  );
}
