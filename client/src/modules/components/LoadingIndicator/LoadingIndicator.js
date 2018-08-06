import React from 'react';
import './LoadingIndicator.css';
import classnames from 'classnames';

export default function LoadingIndicator(props) {
  const allowedProps = {...props};
  delete allowedProps.small;
  return (
    <div className={classnames('LoadingIndicator__div', {small: props.small})} {...allowedProps}>
      <div className="LoadingIndicator__circle1 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle2 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle3 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle4 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle5 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle6 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle7 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle8 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle9 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle10 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle11 LoadingIndicator__circle" />
      <div className="LoadingIndicator__circle12 LoadingIndicator__circle" />
    </div>
  );
}
