/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import './Logo.scss';

export default function Logo(props) {
  const svgProps = {};
  svgProps.fill = props.fill || '#606060';
  const filteredProps = {...props};
  delete filteredProps.fill;
  return (
    <span {...filteredProps} className={classnames('Logo', props.className)}>
      <svg
        xmlns="http://www.w3.org/2000/svg"
        height="100%"
        width="100%"
        viewBox="0 0 180 180"
        {...svgProps}
      >
        <path d="M175.744 54.157c-8.171 19.7-30.722 29.05-50.372 20.9-19.65-8.146-28.962-30.701-20.792-50.391 8.172-19.706 30.728-29.045 50.378-20.9 19.654 8.151 28.962 30.696 20.786 50.39zm-4.95 29.563l.216-.09 4.03 15.935-17.516 7.287-8.399-14.101.647-.269c-6.21 1.145-12.656 1.234-19.072.102l-8.404 14.125-17.511-7.218 4.035-15.95.376.154a53.597 53.597 0 0 1-13.28-13.377l.022.05-15.924 4.072-7.252-17.522 14.107-8.442.262.634c-1.123-6.212-1.199-12.658-.051-19.075l-14.12-8.383 7.27-17.558 15.907 4.022-.194.473a54.442 54.442 0 0 1 7.463-8.59H11.753C5.27 0 0 5.277 0 11.775v76.813l10.81 7.815-.388.401c5.34-3.543 11.187-5.998 17.231-7.413l-.111.004 2.113-16.017 18.643-.296 2.541 15.932-.628.011a52.796 52.796 0 0 1 17.411 6.827l12.79-9.855 13.374 12.989-9.486 13.08-.345-.339c3.5 5.299 5.927 11.098 7.324 17.091v-.044l16.025 2.075.258 18.636-15.952 2.573-.008-.594a53.08 53.08 0 0 1-6.87 17.397L93.315 180h74.736c6.495 0 11.745-5.272 11.745-11.78V75.913a53.98 53.98 0 0 1-9.002 7.808zM66.788 167.384c-14.626 15.017-38.611 15.36-53.6.776-14.985-14.579-15.295-38.556-.675-53.573 14.62-15.017 38.611-15.355 53.6-.782 14.988 14.584 15.293 38.562.675 53.579z" />
      </svg>
    </span>
  );
}
