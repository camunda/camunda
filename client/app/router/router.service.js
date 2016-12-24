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
    getRouteReducer,
    onUrlChange
  };

  $window.onpopstate = onUrlChange;

  return router;

  function onUrlChange() {
    const path = $document.location.pathname + $document.location.search;

    for (let i = 0; i < routes.length; i++) {
      const {name, test} = routes[i];
      const params = test(path);

      if (params) {
        dispatchAction(createRouteAction(name, params));

        break;
      }
    }
  }

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
    const route = findRoute(routeName);

    if (route && typeof route.construct === 'function') {
      return route.construct(params);
    }
  }

  function getRouteReducer(name) {
    const route = findRoute(name);

    return route && route.reducer;
  }

  function findRoute(name) {
    return routes.find(({name: routeName}) => {
      return routeName === name;
    });
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

