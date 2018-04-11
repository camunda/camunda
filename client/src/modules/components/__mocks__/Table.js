import React from 'react';

export default function Table({body}) {
  return <div>{body.map(row => row.map((col, idx) => <div key={idx}>{col}</div>))}</div>;
}
