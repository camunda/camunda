import React from 'react';

import './ErrorPage.css';

export default function Error(props) {
  const errorMessage =
    props.error.errorMessage || props.error.statusText || 'Something went wrong.';
  return (
    <div className="ErrorPage">
      <div className="ErrorPage__content">
        <h1 className="ErrorPage__maledicta">!@#$%&*</h1>
        <h2 className="ErrorPage__error-message">{errorMessage}</h2>
        <p>
          Try again, check if Elasticsearch is up and running, and try to access the{' '}
          <a href="/">Optimize Start Page</a>.
        </p>
      </div>
    </div>
  );
}
