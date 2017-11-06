import React from 'react';

export default function Number({data}) {
  if(!data || typeof data === 'object') {
    return <p>Cannot display data. Choose another visualization.</p>;
  }

  return <p>{data}</p>;
}
