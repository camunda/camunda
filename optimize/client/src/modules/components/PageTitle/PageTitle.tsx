/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {t} from 'translation';

interface PageTitleProps {
  pageName?: string;
  resourceName?: string;
  isNew?: boolean;
}

export default function PageTitle({pageName, resourceName, isNew = false}: PageTitleProps) {
  useEffect(() => {
    if (isNew) {
      setPageTitle(`${t('appName')} | ${t('common.new')} ${pageName}`);
    } else if (pageName && resourceName) {
      setPageTitle(`${t('appName')} | ${pageName} - ${resourceName}`);
    } else if (pageName) {
      setPageTitle(`${t('appName')} | ${pageName}`);
    } else {
      setPageTitle(t('appName'));
    }

    return () => {
      setPageTitle(t('appName'));
    };
  }, [isNew, pageName, resourceName]);

  return null;
}

function setPageTitle(title: string | JSX.Element[]) {
  document.title = title.toString();
}
