/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button} from 'components';
import {withErrorHandling} from 'HOC';
import {get} from 'request';

export function DownloadButton({href, fileName, onClick, mightFail, error, resetError, ...props}) {
  return (
    <Button
      {...props}
      onClick={(evt) => {
        onClick?.(evt);
        mightFail(getData(href), (data) => {
          const hiddenElement = document.createElement('a');
          hiddenElement.href = window.URL.createObjectURL(data);
          hiddenElement.download = fileName || href.substring(href.lastIndexOf('/') + 1);
          hiddenElement.click();
        });
      }}
    />
  );
}

async function getData(url) {
  const response = await get(url);
  return await response.blob();
}

export default withErrorHandling(DownloadButton);
