import React from 'react';

const jsx = React.createElement;

export function Loader({visible, className, style = {position: 'static'}, text}) {
  if (!visible) {
    return null;
  }

  return <div className={'loading_indicator overlay ' + className} style={style}>
    <div className="spinner"><span className="glyphicon glyphicon-refresh spin"></span></div>
    <div className="text">
      {text ? text: 'loading'}
    </div>
  </div>;
}
