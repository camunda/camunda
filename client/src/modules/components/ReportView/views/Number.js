import React from 'react';

import './Number.css';

export default function Number({data, errorMessage}) {
  if(typeof data !== 'number') {
    return <p>{errorMessage}</p>;
  }

  return <span className='Number'>{data}</span>;
}
