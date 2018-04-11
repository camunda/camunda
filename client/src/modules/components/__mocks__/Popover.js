import React from 'react';

export default function Popover({title, children}) {
  return (
    <div>
      {title} {children}
    </div>
  );
}
