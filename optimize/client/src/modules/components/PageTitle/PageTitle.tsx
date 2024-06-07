/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
