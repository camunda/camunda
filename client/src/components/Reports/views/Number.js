import React from 'react';

export default function Number({data, errorMessage}) {
  if(typeof data !== 'number') {
    return <p>{errorMessage}</p>;
  }

  return <p>{data}</p>;
}
