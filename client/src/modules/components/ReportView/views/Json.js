import React from 'react';

import './Json.css';

export default function Json({data}) {
  return <textarea readOnly value={JSON.stringify(data, null, 2)} className="Json__textarea" />;
}
