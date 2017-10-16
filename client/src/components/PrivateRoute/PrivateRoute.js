import React from 'react';
import {Route, Redirect} from 'react-router-dom'
import {getToken} from 'credentials';

export default function PrivateRoute({ component: Component, ...rest }) {
  return (<Route {...rest} render={props => (
    getToken() ? (
      <Component {...props}/>
    ) : (
      <Redirect to={{
        pathname: '/login',
        state: { from: props.location }
      }}/>
    )
  )}/>);
}