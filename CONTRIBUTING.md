# Contributing to Zeebe

## Building zb from Source

## Building the Documentation

The documentation is located under `docs/`.

In order to build the documentation you need the [mdbook](https://github.com/azerupi/mdBook/) utilitity.

The [binaries](https://github.com/azerupi/mdBook/releases) can be downloaded from the github release page.

In order to use mdbook you need to put it into your path. After this you can simply type

```bash
cd docs/
mdbook serve
```

This will assemble the documentation from the sources and serve it under [http://localhost:3000](http://localhost:3000).
