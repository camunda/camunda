/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';

import {getHeader} from 'config';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import './IconLink.scss';

export function IconLink({mightFail, href}) {
  const [config, setConfig] = useState({});

  useEffect(() => {
    mightFail(getHeader(), setConfig, showError);
  }, [mightFail]);

  return (
    <a className="IconLink" href={href} target="_blank" rel="noopener noreferrer">
      <img src={config.logo} alt="Logo" />
    </a>
  );
}

export default withErrorHandling(IconLink);
