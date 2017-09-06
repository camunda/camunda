import React from 'react';
import {Loader} from './Loader.react';

const jsx = React.createElement;

export function LoadingIndicator({loading, children}) {
  if (loading) {
    return <Loader visible={loading} />;
  }

  return children;
}
