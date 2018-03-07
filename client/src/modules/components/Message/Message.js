import React from 'react';
import './Message.css';

export default function Message({type, message}) {
  return <div className={'Message' + (type ? ' Message--' + type : '')}>{message}</div>;
}
