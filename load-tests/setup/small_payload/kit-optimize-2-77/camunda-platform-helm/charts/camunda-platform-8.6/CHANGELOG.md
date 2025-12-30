# Changelog

## [11.11.3](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.6-11.11.2...camunda-platform-8.6-11.11.3) (2025-12-11)


### Bug Fixes

* apply tpl to issuerBackendUrl ([#4858](https://github.com/camunda/camunda-platform-helm/issues/4858)) ([22b5cd7](https://github.com/camunda/camunda-platform-helm/commit/22b5cd74e7a3e952b17f752541c8233c5cd0f185))


### Dependencies

* update camunda-platform-images (patch) ([#4874](https://github.com/camunda/camunda-platform-helm/issues/4874)) ([3099888](https://github.com/camunda/camunda-platform-helm/commit/30998888f89795451f6e8e861b41e50c41707804))
* update patch-updates (patch) ([#4860](https://github.com/camunda/camunda-platform-helm/issues/4860)) ([b059be6](https://github.com/camunda/camunda-platform-helm/commit/b059be61080ee33c8d8ee9cfa5f0f4d2f4cdaf35))


### Refactors

* remove unused identity redirect-url ([#4853](https://github.com/camunda/camunda-platform-helm/issues/4853)) ([90c61e6](https://github.com/camunda/camunda-platform-helm/commit/90c61e66d4676b4ccadee71e6a593ab69df7f6d9))

## [11.11.2](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.6-11.11.1...camunda-platform-8.6-11.11.2) (2025-12-03)


### Bug Fixes

* add back zeebe secret referenced as string ([#4822](https://github.com/camunda/camunda-platform-helm/issues/4822)) ([9eaf035](https://github.com/camunda/camunda-platform-helm/commit/9eaf035cc70b918a6dad54452438ef230efa8b37))


### Dependencies

* update camunda-platform-images (patch) ([#4830](https://github.com/camunda/camunda-platform-helm/issues/4830)) ([02793c0](https://github.com/camunda/camunda-platform-helm/commit/02793c0cea5cd70ae1e327510a00230fdbaa3ef1))
* update patch-updates (patch) ([#4831](https://github.com/camunda/camunda-platform-helm/issues/4831)) ([c77bbe5](https://github.com/camunda/camunda-platform-helm/commit/c77bbe52c428f1a22597a76c19c0b26a40d6a8b7))

## [11.11.1](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.6-11.11.0...camunda-platform-8.6-11.11.1) (2025-11-28)


### Dependencies

* update camunda-platform-images (patch) ([#4792](https://github.com/camunda/camunda-platform-helm/issues/4792)) ([fd7294c](https://github.com/camunda/camunda-platform-helm/commit/fd7294c95d621b4d7d1c1d290b703d6209e61b44))
* update patch-updates ([#4761](https://github.com/camunda/camunda-platform-helm/issues/4761)) ([89f5551](https://github.com/camunda/camunda-platform-helm/commit/89f55518ddeaeec8fb0423afd173cd39e631ea95))

## [11.11.0](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.6-11.10.3...camunda-platform-8.6-11.11.0) (2025-11-25)


### Features

* backport custom client creation to 8.6 and 8.9 ([#4710](https://github.com/camunda/camunda-platform-helm/issues/4710)) ([68bec54](https://github.com/camunda/camunda-platform-helm/commit/68bec54d8f2e7147c2f75ff20c3314533ce0c3a7))
* define custom users through values.yaml ([#4670](https://github.com/camunda/camunda-platform-helm/issues/4670)) ([19ab9eb](https://github.com/camunda/camunda-platform-helm/commit/19ab9eb7e42fe84b76118a1930dd72bb6d302cdf))


### Bug Fixes

* incorrect example for keycloak in readme.md ([#4586](https://github.com/camunda/camunda-platform-helm/issues/4586)) ([f6bf0a9](https://github.com/camunda/camunda-platform-helm/commit/f6bf0a9c125178b2cd3b15d465dc7ed0a59893b8))
* remove client env vars from qa scenario files ([#4726](https://github.com/camunda/camunda-platform-helm/issues/4726)) ([2c9ea12](https://github.com/camunda/camunda-platform-helm/commit/2c9ea121df9f402b19330e61dddbdd28ffbd4d35))


### Dependencies

* update camunda-platform-images (patch) ([#4763](https://github.com/camunda/camunda-platform-helm/issues/4763)) ([3fb6957](https://github.com/camunda/camunda-platform-helm/commit/3fb6957238e9daa4e7c6acdc9357dc6adeb2c0a9))
* update camunda-platform-images (patch) ([#4777](https://github.com/camunda/camunda-platform-helm/issues/4777)) ([18de26f](https://github.com/camunda/camunda-platform-helm/commit/18de26fe264049929c2edddb8e9aa04f3c213b94))
* update minor-updates (minor) ([#4712](https://github.com/camunda/camunda-platform-helm/issues/4712)) ([4cf435c](https://github.com/camunda/camunda-platform-helm/commit/4cf435c5aa989eaab1b0dde9cbc75fb694774854))
* update minor-updates (minor) ([#4765](https://github.com/camunda/camunda-platform-helm/issues/4765)) ([54dc74d](https://github.com/camunda/camunda-platform-helm/commit/54dc74d5fed86702a26a63f247d7ccc25424946a))
* update module golang.org/x/crypto to v0.45.0 [security] ([#4745](https://github.com/camunda/camunda-platform-helm/issues/4745)) ([1b31ade](https://github.com/camunda/camunda-platform-helm/commit/1b31aded5d1e7297e9648ad2e225b86f716a3b3e))
* update patch-updates (patch) ([#4762](https://github.com/camunda/camunda-platform-helm/issues/4762)) ([f8e7bbd](https://github.com/camunda/camunda-platform-helm/commit/f8e7bbd242097bb2c7c7bfde54aa53b3a5077af2))
