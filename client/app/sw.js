const ASSETS_CACHE = `ASSETS-CACHE_${process.env.version}`;
const API_CACHE = `API_CACHE_${process.env.version}`;
const assets = global.serviceWorkerOption ? global.serviceWorkerOption.assets : [];

const whiteListApis = [
  /\/api\/process-definition\/[^\/]+\/xml$/,
  /\/api\/process-definition\/[^\/]+\/variables$/,
  /\/api\/process-definition\/xml/
];

let assetsToCache = [
  ...assets,
  './',
];

assetsToCache = assetsToCache.map((path) => {
  return new URL(path, global.location).toString();
});

self.addEventListener('install', event => {
  event.waitUntil(
    global.caches
      .open(ASSETS_CACHE)
      .then(cache => {
        return cache.addAll(assetsToCache);
      })
  );
});

self.addEventListener('activate', (event) => {
  // Clean old caches
  event.waitUntil(
    global.caches
      .keys()
      .then((cacheNames) => {
        return Promise.all(
          cacheNames.map((cacheName) => {
            // Delete the caches that are not the current one.
            if ([API_CACHE, ASSETS_CACHE].indexOf(cacheName) >= 0) {
              return;
            }

            return global.caches.delete(cacheName);
          })
        );
      })
  );
});

self.addEventListener('fetch', event => {
  if (/api/.test(event.request.url)) {
    return handleApiRequest(event);
  }

  handleAssetsRequests(event);
});

function handleApiRequest(event) {
  const {request} = event;

  if (!isRequestCachable(request)) {
    return event.respondWith(fetch(request));
  }

  const responsePromise = fetchAndUpdate(request.clone(), API_CACHE);

  event.respondWith(
    caches
      .match(request)
      .then(response => {
        if (!response) {
          return responsePromise;
        }

        return response;
      })
  );

  event.waitUntil(
    responsePromise
  );
}

function isRequestCachable(request) {
  return request.method.toUpperCase() === 'GET' &&
    whiteListApis.some(regExp => regExp.test(request.url));
}

function handleAssetsRequests(event) {
  const {request} = event;

  return event.respondWith(
    caches
      .match(request)
      .then(response =>  {
        return response || fetchAndUpdate(request, ASSETS_CACHE);
      })
  );
}

function fetchAndUpdate(request, cacheName) {
  return fetch(request)
    .then(response => {
      if (response.type === 'opaqueredirect') {
        // delete old cached resource and don't cache redirect response
        return caches
          .open(cacheName)
          .then(cache => {
            return cache.delete(request);
          })
          .then(() => response);
      }

      const responseClone = response.clone();

      return caches.open(cacheName)
        .then(cache => {
          cache.put(
            request,
            response
          );
        })
        .then(() => responseClone);
    });
}
