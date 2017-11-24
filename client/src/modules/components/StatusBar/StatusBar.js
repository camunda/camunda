import React from 'react';

import './StatusBar.css';

export default function StatusBar(props) {
  /* Allow for passing in a height prop and merge it with any additonal style props: */
  const style = props.style || {};
  style.height = props.height;
  /* Define a width based on props.status and merge with style prop: */
  const indicatorStyle = props.indicatorStyle || {};
  indicatorStyle.width = props.status + '%';

  const title = props.title ? props.title + ' ' : '';

  return (
    <div {...props} className={'StatusBar ' + (props.className || '')} style={style} title={title + props.status + '%'} >
      <div className='StatusBar__indicator' style={indicatorStyle}></div>
    </div>
  );
}
