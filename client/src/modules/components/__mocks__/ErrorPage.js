import React from 'react';

export default function ErrorPage(props) {
  const errorMessage =
    props.errorMessage ||
    props.error.errorMessage ||
    props.error.statusText ||
    'Something went wrong.';
  return <div>{`error page error: ${errorMessage}`}</div>;
}
