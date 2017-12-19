import React from 'react';

import './Icon.css';

export default function Button(props) {
  const filteredProps = {...props};
  const src = props.src || 'plus';
  delete filteredProps.tag;
  delete filteredProps.src;
  delete filteredProps.backgroundImg;
  const Tag = props.tag || 'span'; 
  
  /* For every icon added:
      1. add {...filteredProps} in the icon source
      2. replace hyphenated attrs with camelcased versions for JSX (fill-rule -> filleRule) in the icon source
      3. Make sure icon has a viewBox attribute that corelates with the actual dimensions inside the svg
      
      Ideally, SVGs should be read from disk and rendered inline in the markup. asfiu this is currently not possible without ejecting from create-react-app as it would require using a custom loader polugin for svg files.
  */

  const icons = {
    plus: <svg xmlns="http://www.w3.org/2000/svg" className='Icon__svg' viewBox='0 0 64 64' {...filteredProps}><g fillRule="evenodd"><path d="M26 2h12v60H26z"/><path d="M62 26v12H2V26z"/></g></svg>,
    edit: <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" {...filteredProps}><path d="M1 15l1.5-4L5 13.5 1 15zM3.04 10.02l2.97 2.93 8.97-8.94L12 1l-8.96 9.02z"/><path fill="none" d="M0 0h16v16H0z"/></svg>,
    close: <svg width="24" height="24" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" {...filteredProps}><path d="M7.757 12L.686 4.929 4.93.686 12 7.756l7.071-7.07 4.243 4.243-7.07 7.07 7.071 7.072-4.244 4.243L12 16.243l-7.071 7.071-4.243-4.243L7.757 12z" fill-rule="evenodd"/></svg>  
  }
  
  /* TODO: only pass whitelisted filteredProps to SVG element (fill, anything else?), the rest should hit parent span? */ 
   
  if (props.backgroundImg) {
    return (
      <Tag {...filteredProps} className={'Icon Icon--' + src}>
      </Tag>
    );
  } else {
    return (
      <span className='Icon Icon--svg' >
        {icons[src]}
      </span>
    )
  } 

}
