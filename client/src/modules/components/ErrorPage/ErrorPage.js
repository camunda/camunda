/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Link} from 'react-router-dom';

import './ErrorPage.scss';

const defaultMessage = 'Something went wrong.';
const defaultDescription = (
  <p>
    Try again, check if Elasticsearch is up and running, and try to access the{' '}
    <a href="/">Optimize Start Page</a>.
  </p>
);

export default function ErrorPage(props) {
  const error = {
    message: '',
    description: ''
  };

  if (props.entity && props.statusCode) {
    constructErrorFromStatusCode(props, error);
  } else {
    extractErrorFromProps(props, error);
  }

  return (
    <div className="ErrorPage">
      <div className="ErrorPage__content">
        <h1 className="ErrorPage__maledicta">!@#$%&*</h1>
        <h2 className="ErrorPage__error-message">{error.message}</h2>
        {error.description}
      </div>
    </div>
  );
}

function extractErrorFromProps(props, error) {
  error.message =
    props.errorMessage || props.error.errorMessage || props.error.statusText || defaultMessage;
  error.description = props.description || defaultDescription;
}

function constructErrorFromStatusCode({entity, statusCode}, error) {
  switch (statusCode) {
    case 404:
      error.message = `This ${entity} does not exist.`;
      error.description = (
        <div>
          <div>{`Please access the ${entity}s page to see a list of all available reports!`}</div>
          <Link to={`/${entity}s/`}>{`Go to ${entity}s list page`}</Link>
        </div>
      );
      break;
    case 403:
      error.message = `You are not authorized to see the ${entity}.`;
      error.description = (
        <div>
          <div>{`Please access the ${entity}s page to see a list of all available reports!`}</div>
          <Link to={`/${entity}s/`}>{`Go to ${entity}s list page`}</Link>
        </div>
      );
      break;
    default:
      error.message = defaultMessage;
      error.description = defaultDescription;
  }
}
