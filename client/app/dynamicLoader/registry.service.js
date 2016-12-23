let modules = {};
let loaders = {};
let listeners = {};

export function addModuleLoader(name, loader) {
  loaders[name] = loader;
}

export function getModule(name) {
  if (modules[name]) {
    return Promise.resolve(modules[name]);
  }

  if (loaders[name]) {
    const loader = loaders[name];

    return loader().then(module => {
      modules[name] = module;

      if (listeners[name]) {
        listeners[name].forEach(listener => listener(module));

        delete listeners[name];
      }

      return module;
    });
  }

  return Promise.reject(
    new Error(`Module ${name} could not be found!`)
  );
}

export function onModuleLoaded(name) {
  if (modules[name]) {
    return Promise.resolve(modules[name]);
  }

  if (!listeners[name]) {
    listeners[name] = [];
  }

  return new Promise((resolve) => {
    listeners[name].push(resolve);
  });
}
