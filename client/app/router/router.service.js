import {$window, $document, dispatchAction} from 'view-utils';
import {createRouteAction} from './router.reducer';

let router;

export function getRouter() {
  if (!router) {
    router = createNewRouter();
  }

  return router;
}

function createNewRouter() {
  let routes = [];
  const history = $window.history;
  const router = {
    addRoutes,
    goTo,
    getUrl,
    getRouteReducer
  };

  window.onpopstate = () => {
    const path = $document.location.pathname + $document.location.search;

    for (let {test, name} of routes) {
      const params = test(path);

      if (!params) {
        dispatchAction(createRouteAction(name, params));

        break;
      }
    }
  };

  return router;

  function addRoutes(...newRoutes) {
    routes = routes.concat(
      newRoutes.map(createRoute)
    );

    return router;
  }

  function goTo(routeName, params, replace) {
    const url = getUrl(routeName, params);

    if (!url) {
      return;
    }

    if (replace) {
      history.replaceState(null, null, url);
    } else {
      history.pushState(null, null, url);
    }

    dispatchAction(createRouteAction(routeName, params));
  }

  function getUrl(routeName, params) {
    for (let {name, construct} of routes) {
      if (name === routeName) {
        return construct(params);
      }
    }
  }

  function getRouteReducer(name) {
    for (let {name: routeName, reducer} of routes) {
      if (name === routeName) {
        return reducer;
      }
    }
  }
}

function createRoute({name, url, test, construct, reducer}) {
  return {
    name,
    url,
    reducer,
    test: typeof test === 'function' ? test : createUrlTestForRoute(url),
    construct: typeof construct === 'function' ? construct : createUrlConstructForRoute(url)
  };
}

export function createUrlTestForRoute(patternUrl) {
  const splitingRegExp = /[\/?&=]/;
  const patternParts = patternUrl.split(splitingRegExp);

  return (url) => {
    const urlParts = url.split(splitingRegExp);

    if (patternParts.length !== urlParts.length) {
      return;
    }

    return patternParts.reduce((params, patternPart, index) => {
      if (!params) {
        return null;
      }

      const urlPart = urlParts[index];

      if (patternPart[0] === ':') {
        const name = patternPart.substr(1);

        params[name] = urlPart;
      } else if (patternPart !== urlPart) {
        return null;
      }

      return params;
    }, {});
  }
}

export function createUrlConstructForRoute(patternUrl) {
  const constantParts = patternUrl
    .split(/:\w+/)
    .filter(part => part.length > 0);
  const variableParts = patternUrl
    .match(/:\w+/g);

  return (params = {}) => {
    if (!variableParts) {
      return patternUrl; //it is just an constant url
    }

    return constantParts.reduce((url, constantPart, index) => {
      const name = variableParts[index] ? variableParts[index].substr(1) : null;

      return url + constantPart + (name ? params[name] : '');
    }, '');
  };
}

