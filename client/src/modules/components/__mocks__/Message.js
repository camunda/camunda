import React from 'react';

export default function Message({type}) {
  return <div className={'Message Message--' + type} />;
}
