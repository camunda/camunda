import React from 'react';

export default function Message({type, children}) {
  return <div className={'Message Message--' + type}>{children}</div>;
}
