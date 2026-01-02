# Changelog

## [13.3.2](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.8-13.3.1...camunda-platform-8.8-13.3.2) (2025-12-11)


### Dependencies

* update camunda-platform-images (patch) ([#4885](https://github.com/camunda/camunda-platform-helm/issues/4885)) ([4ffcd1d](https://github.com/camunda/camunda-platform-helm/commit/4ffcd1dbde8b44b82def6dcb320330c5197e1cd1))

## [13.3.1](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.8-13.3.0...camunda-platform-8.8-13.3.1) (2025-12-10)


### Bug Fixes

* apply tpl to issuerBackendUrl ([#4858](https://github.com/camunda/camunda-platform-helm/issues/4858)) ([22b5cd7](https://github.com/camunda/camunda-platform-helm/commit/22b5cd74e7a3e952b17f752541c8233c5cd0f185))


### Dependencies

* update camunda-platform-digests ([#4846](https://github.com/camunda/camunda-platform-helm/issues/4846)) ([e89a081](https://github.com/camunda/camunda-platform-helm/commit/e89a081f6c53c7b8676917c88c1761d1c07ddc5c))
* update camunda-platform-digests ([#4856](https://github.com/camunda/camunda-platform-helm/issues/4856)) ([1994d36](https://github.com/camunda/camunda-platform-helm/commit/1994d369ec157bf0b474c8e83a59a71ddf8e7ba8))
* update camunda-platform-images (patch) ([#4848](https://github.com/camunda/camunda-platform-helm/issues/4848)) ([bcc02e8](https://github.com/camunda/camunda-platform-helm/commit/bcc02e832939bfcb6fa643befa11ef0701a883f7))
* update camunda-platform-images (patch) ([#4874](https://github.com/camunda/camunda-platform-helm/issues/4874)) ([3099888](https://github.com/camunda/camunda-platform-helm/commit/30998888f89795451f6e8e861b41e50c41707804))
* update camunda/optimize docker tag to v8.8.3 ([#4875](https://github.com/camunda/camunda-platform-helm/issues/4875)) ([a76574c](https://github.com/camunda/camunda-platform-helm/commit/a76574c5b23e3f3d5a20df03fb06bb799d2409f6))
* update patch-updates (patch) ([#4860](https://github.com/camunda/camunda-platform-helm/issues/4860)) ([b059be6](https://github.com/camunda/camunda-platform-helm/commit/b059be61080ee33c8d8ee9cfa5f0f4d2f4cdaf35))


### Refactors

* remove unused identity redirect-url ([#4853](https://github.com/camunda/camunda-platform-helm/issues/4853)) ([90c61e6](https://github.com/camunda/camunda-platform-helm/commit/90c61e66d4676b4ccadee71e6a593ab69df7f6d9))

## [13.3.0](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.8-13.2.2...camunda-platform-8.8-13.3.0) (2025-12-03)


### Features

* support password field for jks ([#4782](https://github.com/camunda/camunda-platform-helm/issues/4782)) ([1877932](https://github.com/camunda/camunda-platform-helm/commit/187793214ca68d989142120a9a018dbe38809deb))


### Bug Fixes

* correct extraVolumeMounts binding in importer deployment ([#4829](https://github.com/camunda/camunda-platform-helm/issues/4829)) ([beb6db3](https://github.com/camunda/camunda-platform-helm/commit/beb6db3115649cb6d8617ee60637ebe6f315b9a7))
* modeler webapp to websockets connection not using override option ([#4812](https://github.com/camunda/camunda-platform-helm/issues/4812)) ([339da02](https://github.com/camunda/camunda-platform-helm/commit/339da02a87add81852177530d2d3b0d5937dd73e))
* replace SNAPSHOT tags with stable versions in 8.8 values-digest.yaml ([#4826](https://github.com/camunda/camunda-platform-helm/issues/4826)) ([9b43fff](https://github.com/camunda/camunda-platform-helm/commit/9b43fff499e399072d9d08cb0365ac2886b0b654))


### Dependencies

* update camunda-platform-digests ([#4818](https://github.com/camunda/camunda-platform-helm/issues/4818)) ([965345c](https://github.com/camunda/camunda-platform-helm/commit/965345c6f3f5fbbff806e15c0781baf55710af9f))
* update camunda-platform-digests ([#4828](https://github.com/camunda/camunda-platform-helm/issues/4828)) ([5b459cb](https://github.com/camunda/camunda-platform-helm/commit/5b459cbb7442c04f1f39e6b6d7b76c45dbd854a0))
* update camunda-platform-images (patch) ([#4830](https://github.com/camunda/camunda-platform-helm/issues/4830)) ([02793c0](https://github.com/camunda/camunda-platform-helm/commit/02793c0cea5cd70ae1e327510a00230fdbaa3ef1))
* update patch-updates (patch) ([#4831](https://github.com/camunda/camunda-platform-helm/issues/4831)) ([c77bbe5](https://github.com/camunda/camunda-platform-helm/commit/c77bbe52c428f1a22597a76c19c0b26a40d6a8b7))

## [13.2.2](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.8-13.2.1...camunda-platform-8.8-13.2.2) (2025-11-28)


### Bug Fixes

* let helm chart support hybrid auth ([#4785](https://github.com/camunda/camunda-platform-helm/issues/4785)) ([cb06ece](https://github.com/camunda/camunda-platform-helm/commit/cb06ece477535c069b03ab5eff3729d9baf93d0a))


### Dependencies

* update camunda-platform-images (patch) ([#4792](https://github.com/camunda/camunda-platform-helm/issues/4792)) ([fd7294c](https://github.com/camunda/camunda-platform-helm/commit/fd7294c95d621b4d7d1c1d290b703d6209e61b44))
* update camunda/console docker tag to v8.8.52 ([#4803](https://github.com/camunda/camunda-platform-helm/issues/4803)) ([f499bc8](https://github.com/camunda/camunda-platform-helm/commit/f499bc812711b0f1cf425350637c175ed9c51609))
* update patch-updates ([#4761](https://github.com/camunda/camunda-platform-helm/issues/4761)) ([89f5551](https://github.com/camunda/camunda-platform-helm/commit/89f55518ddeaeec8fb0423afd173cd39e631ea95))

## [13.2.1](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.8-13.2.0...camunda-platform-8.8-13.2.1) (2025-11-25)


### Bug Fixes

* remove conditional rendering from management identity configmap ([#4771](https://github.com/camunda/camunda-platform-helm/issues/4771)) ([0dff2df](https://github.com/camunda/camunda-platform-helm/commit/0dff2df28c565b7d75722cd87c18a1dd82433a01))


### Dependencies

* update camunda-platform-images (patch) ([#4777](https://github.com/camunda/camunda-platform-helm/issues/4777)) ([18de26f](https://github.com/camunda/camunda-platform-helm/commit/18de26fe264049929c2edddb8e9aa04f3c213b94))

## [13.2.0](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.8-13.1.2...camunda-platform-8.8-13.2.0) (2025-11-21)


### Features

* backport custom client creation to 8.6 and 8.9 ([#4710](https://github.com/camunda/camunda-platform-helm/issues/4710)) ([68bec54](https://github.com/camunda/camunda-platform-helm/commit/68bec54d8f2e7147c2f75ff20c3314533ce0c3a7))
* define custom clients for management identity ([#4653](https://github.com/camunda/camunda-platform-helm/issues/4653)) ([b488a0b](https://github.com/camunda/camunda-platform-helm/commit/b488a0bfd44c3bf6558edcd96c15cdd2f3eb4b5f))
* define custom users through values.yaml ([#4670](https://github.com/camunda/camunda-platform-helm/issues/4670)) ([19ab9eb](https://github.com/camunda/camunda-platform-helm/commit/19ab9eb7e42fe84b76118a1930dd72bb6d302cdf))


### Bug Fixes

* 8.8 values-latest.yaml no longer references SNAPSHOT images ([#4727](https://github.com/camunda/camunda-platform-helm/issues/4727)) ([b9e560f](https://github.com/camunda/camunda-platform-helm/commit/b9e560f6baf44d84c53095f3faa1566dd2da71b4))
* allow for custom jks in migration-data job ([#4722](https://github.com/camunda/camunda-platform-helm/issues/4722)) ([978d127](https://github.com/camunda/camunda-platform-helm/commit/978d12770818badf84906fa46e3def1e68e704c9))
* connectors prefix should not be present if connectors is disabled ([#4570](https://github.com/camunda/camunda-platform-helm/issues/4570)) ([43648ff](https://github.com/camunda/camunda-platform-helm/commit/43648ffffe09aa60ea2f0eb9bd5cfdf58423a623))
* extraVolumeClaimTemplateTemplate indent for orchestration cluster ([#4697](https://github.com/camunda/camunda-platform-helm/issues/4697)) ([4c5387f](https://github.com/camunda/camunda-platform-helm/commit/4c5387f03688ab9c510e45dc92f97f7c0da9fac7))
* incorrect example for keycloak in readme.md ([#4586](https://github.com/camunda/camunda-platform-helm/issues/4586)) ([f6bf0a9](https://github.com/camunda/camunda-platform-helm/commit/f6bf0a9c125178b2cd3b15d465dc7ed0a59893b8))
* orchestration migration configurable init container image ([#4719](https://github.com/camunda/camunda-platform-helm/issues/4719)) ([f3174a0](https://github.com/camunda/camunda-platform-helm/commit/f3174a085c691df7f4c8048b08c87381c1afdacd))
* remove client env vars from qa scenario files ([#4726](https://github.com/camunda/camunda-platform-helm/issues/4726)) ([2c9ea12](https://github.com/camunda/camunda-platform-helm/commit/2c9ea121df9f402b19330e61dddbdd28ffbd4d35))
* remove leftover console secret constraints ([#4749](https://github.com/camunda/camunda-platform-helm/issues/4749)) ([80bd4de](https://github.com/camunda/camunda-platform-helm/commit/80bd4de8835b1216ed4ce52cc55b959f18d09d9a))
* setting zeebe image tag shouldnt disable broker profile ([#4587](https://github.com/camunda/camunda-platform-helm/issues/4587)) ([c2b0c7a](https://github.com/camunda/camunda-platform-helm/commit/c2b0c7a1bd6b9411c56dd17fde017058a1e2fabb))
* typo in webModeler pvc ([#4699](https://github.com/camunda/camunda-platform-helm/issues/4699)) ([d6a08c5](https://github.com/camunda/camunda-platform-helm/commit/d6a08c517fce6cd8e7eea7f59cb09790367e878c))
* typo lower case values ([#4737](https://github.com/camunda/camunda-platform-helm/issues/4737)) ([2ec2710](https://github.com/camunda/camunda-platform-helm/commit/2ec2710830d669e53a709bbb176c58ba064e12f2))


### Dependencies

* update camunda-platform-digests ([#4694](https://github.com/camunda/camunda-platform-helm/issues/4694)) ([b068811](https://github.com/camunda/camunda-platform-helm/commit/b0688112c9a505bd551f5795119a354bdb63afc9))
* update camunda-platform-digests ([#4702](https://github.com/camunda/camunda-platform-helm/issues/4702)) ([751b22b](https://github.com/camunda/camunda-platform-helm/commit/751b22bdfa0978a6a044d06310a303a1517f771f))
* update camunda-platform-digests ([#4704](https://github.com/camunda/camunda-platform-helm/issues/4704)) ([9c31cdc](https://github.com/camunda/camunda-platform-helm/commit/9c31cdc697a4cf9e60fda4c392b8213e9101537d))
* update camunda-platform-digests ([#4720](https://github.com/camunda/camunda-platform-helm/issues/4720)) ([8d69681](https://github.com/camunda/camunda-platform-helm/commit/8d696810a230633f09ff0b4a2921b7e4c954f832))
* update camunda-platform-digests ([#4724](https://github.com/camunda/camunda-platform-helm/issues/4724)) ([390de99](https://github.com/camunda/camunda-platform-helm/commit/390de99e51b6169aeb9ba6c44f9a84fb0f8e0d1a))
* update camunda-platform-digests ([#4743](https://github.com/camunda/camunda-platform-helm/issues/4743)) ([4a2c32a](https://github.com/camunda/camunda-platform-helm/commit/4a2c32a97b1b614a0b6f09a1d1adf78055fc1a4e))
* update camunda-platform-images (patch) ([#4713](https://github.com/camunda/camunda-platform-helm/issues/4713)) ([7c59886](https://github.com/camunda/camunda-platform-helm/commit/7c59886d69d49d702bd5b3e1acf5cf22a7af38bf))
* update camunda-platform-images (patch) ([#4732](https://github.com/camunda/camunda-platform-helm/issues/4732)) ([3445429](https://github.com/camunda/camunda-platform-helm/commit/3445429910e81e4077f8702535ee73659e35bff4))
* update minor-updates (minor) ([#4712](https://github.com/camunda/camunda-platform-helm/issues/4712)) ([4cf435c](https://github.com/camunda/camunda-platform-helm/commit/4cf435c5aa989eaab1b0dde9cbc75fb694774854))
* update module golang.org/x/crypto to v0.45.0 [security] ([#4745](https://github.com/camunda/camunda-platform-helm/issues/4745)) ([1b31ade](https://github.com/camunda/camunda-platform-helm/commit/1b31aded5d1e7297e9648ad2e225b86f716a3b3e))
