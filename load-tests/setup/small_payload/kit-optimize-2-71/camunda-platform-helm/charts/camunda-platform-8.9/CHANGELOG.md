# Changelog

## [14.0.0-alpha2](https://github.com/camunda/camunda-platform-helm/compare/camunda-platform-8.9-14.0.0-alpha1...camunda-platform-8.9-14.0.0-alpha2) (2025-12-05)


### Features

* add RDBMS support to 8.9 helm charts ([#4572](https://github.com/camunda/camunda-platform-helm/issues/4572)) ([342569e](https://github.com/camunda/camunda-platform-helm/commit/342569e0e2c0c94c555c3886c1b4a2b225542662))
* backport custom client creation to 8.6 and 8.9 ([#4710](https://github.com/camunda/camunda-platform-helm/issues/4710)) ([68bec54](https://github.com/camunda/camunda-platform-helm/commit/68bec54d8f2e7147c2f75ff20c3314533ce0c3a7))
* define custom users through values.yaml ([#4670](https://github.com/camunda/camunda-platform-helm/issues/4670)) ([19ab9eb](https://github.com/camunda/camunda-platform-helm/commit/19ab9eb7e42fe84b76118a1930dd72bb6d302cdf))
* enhance Keycloak integration with realm support and additional client configurations ([911ad7a](https://github.com/camunda/camunda-platform-helm/commit/911ad7a93f41a5b5be6ffffc6e182d55ab867f8c))


### Bug Fixes

* 8.9 version bumps for alpha2 ([#4843](https://github.com/camunda/camunda-platform-helm/issues/4843)) ([4330e7f](https://github.com/camunda/camunda-platform-helm/commit/4330e7fadd3d4ec95054fbf1cf13a32412789e6f))
* add requestBodySize to orchestration multipart config ([#4838](https://github.com/camunda/camunda-platform-helm/issues/4838)) ([8acc157](https://github.com/camunda/camunda-platform-helm/commit/8acc157bacd52c64ff1c480f56d88fd01042b1a1))
* align 8.9 retention config with Wave 1 property names ([#4813](https://github.com/camunda/camunda-platform-helm/issues/4813)) ([65cf7ab](https://github.com/camunda/camunda-platform-helm/commit/65cf7ab3a68f6ede6e163845a4ce3b3051136f7e))
* enable auto-ddl by default ([#4821](https://github.com/camunda/camunda-platform-helm/issues/4821)) ([3d1767c](https://github.com/camunda/camunda-platform-helm/commit/3d1767cffd683db2f9d0ca937daabe3badea7982))
* extraVolumeClaimTemplateTemplate indent for orchestration cluster ([#4697](https://github.com/camunda/camunda-platform-helm/issues/4697)) ([4c5387f](https://github.com/camunda/camunda-platform-helm/commit/4c5387f03688ab9c510e45dc92f97f7c0da9fac7))
* incorrect example for keycloak in readme.md ([#4586](https://github.com/camunda/camunda-platform-helm/issues/4586)) ([f6bf0a9](https://github.com/camunda/camunda-platform-helm/commit/f6bf0a9c125178b2cd3b15d465dc7ed0a59893b8))
* let helm chart support hybrid auth ([#4785](https://github.com/camunda/camunda-platform-helm/issues/4785)) ([cb06ece](https://github.com/camunda/camunda-platform-helm/commit/cb06ece477535c069b03ab5eff3729d9baf93d0a))
* modeler webapp to websockets connection not using override option ([#4812](https://github.com/camunda/camunda-platform-helm/issues/4812)) ([339da02](https://github.com/camunda/camunda-platform-helm/commit/339da02a87add81852177530d2d3b0d5937dd73e))
* refactor tls secrets to use new pattern ([#4599](https://github.com/camunda/camunda-platform-helm/issues/4599)) ([ec98d12](https://github.com/camunda/camunda-platform-helm/commit/ec98d12167c05b959d29d5805630b931efe64a13))
* remap replicas key from legacy to new keys ([#4554](https://github.com/camunda/camunda-platform-helm/issues/4554)) ([7019e4d](https://github.com/camunda/camunda-platform-helm/commit/7019e4d09784b04357465b8ef39f67050c92b6da))
* remove client env vars from qa scenario files ([#4726](https://github.com/camunda/camunda-platform-helm/issues/4726)) ([2c9ea12](https://github.com/camunda/camunda-platform-helm/commit/2c9ea121df9f402b19330e61dddbdd28ffbd4d35))
* remove conditional rendering from management identity configmap ([#4771](https://github.com/camunda/camunda-platform-helm/issues/4771)) ([0dff2df](https://github.com/camunda/camunda-platform-helm/commit/0dff2df28c565b7d75722cd87c18a1dd82433a01))
* remove leftover console secret constraints ([#4749](https://github.com/camunda/camunda-platform-helm/issues/4749)) ([80bd4de](https://github.com/camunda/camunda-platform-helm/commit/80bd4de8835b1216ed4ce52cc55b959f18d09d9a))
* return none for authMethod when component is disabled ([#4810](https://github.com/camunda/camunda-platform-helm/issues/4810)) ([e69c0b7](https://github.com/camunda/camunda-platform-helm/commit/e69c0b7618dcee5d7bd1f6dd844614baca709a7e))
* typo lower case values ([#4737](https://github.com/camunda/camunda-platform-helm/issues/4737)) ([2ec2710](https://github.com/camunda/camunda-platform-helm/commit/2ec2710830d669e53a709bbb176c58ba064e12f2))
* **web-modeler:** align pusher secret usage across components ([#4769](https://github.com/camunda/camunda-platform-helm/issues/4769)) ([bf225b1](https://github.com/camunda/camunda-platform-helm/commit/bf225b13a2c54aa64841e7f421131260a1ef2098))


### Dependencies

* update camunda-platform-digests ([#4704](https://github.com/camunda/camunda-platform-helm/issues/4704)) ([9c31cdc](https://github.com/camunda/camunda-platform-helm/commit/9c31cdc697a4cf9e60fda4c392b8213e9101537d))
* update camunda-platform-digests ([#4720](https://github.com/camunda/camunda-platform-helm/issues/4720)) ([8d69681](https://github.com/camunda/camunda-platform-helm/commit/8d696810a230633f09ff0b4a2921b7e4c954f832))
* update camunda-platform-digests ([#4724](https://github.com/camunda/camunda-platform-helm/issues/4724)) ([390de99](https://github.com/camunda/camunda-platform-helm/commit/390de99e51b6169aeb9ba6c44f9a84fb0f8e0d1a))
* update camunda-platform-digests ([#4743](https://github.com/camunda/camunda-platform-helm/issues/4743)) ([4a2c32a](https://github.com/camunda/camunda-platform-helm/commit/4a2c32a97b1b614a0b6f09a1d1adf78055fc1a4e))
* update camunda-platform-digests ([#4772](https://github.com/camunda/camunda-platform-helm/issues/4772)) ([e44a39b](https://github.com/camunda/camunda-platform-helm/commit/e44a39bc0621393869a94bd026a87042130da061))
* update camunda-platform-digests ([#4787](https://github.com/camunda/camunda-platform-helm/issues/4787)) ([862082d](https://github.com/camunda/camunda-platform-helm/commit/862082d8bb3ce85d499f500140f2029706eab472))
* update camunda-platform-digests ([#4816](https://github.com/camunda/camunda-platform-helm/issues/4816)) ([ac05efc](https://github.com/camunda/camunda-platform-helm/commit/ac05efc33cf8ce730dda8a8878c660e6bdbbb65a))
* update camunda-platform-digests ([#4818](https://github.com/camunda/camunda-platform-helm/issues/4818)) ([965345c](https://github.com/camunda/camunda-platform-helm/commit/965345c6f3f5fbbff806e15c0781baf55710af9f))
* update camunda-platform-digests ([#4828](https://github.com/camunda/camunda-platform-helm/issues/4828)) ([5b459cb](https://github.com/camunda/camunda-platform-helm/commit/5b459cbb7442c04f1f39e6b6d7b76c45dbd854a0))
* update camunda-platform-digests ([#4840](https://github.com/camunda/camunda-platform-helm/issues/4840)) ([6d6c2b4](https://github.com/camunda/camunda-platform-helm/commit/6d6c2b4ae96671b2c1e5405e79a5a9f67acb9677))
* update camunda-platform-images (patch) ([#4713](https://github.com/camunda/camunda-platform-helm/issues/4713)) ([7c59886](https://github.com/camunda/camunda-platform-helm/commit/7c59886d69d49d702bd5b3e1acf5cf22a7af38bf))
* update camunda-platform-images (patch) ([#4792](https://github.com/camunda/camunda-platform-helm/issues/4792)) ([fd7294c](https://github.com/camunda/camunda-platform-helm/commit/fd7294c95d621b4d7d1c1d290b703d6209e61b44))
* update minor-updates (minor) ([#4712](https://github.com/camunda/camunda-platform-helm/issues/4712)) ([4cf435c](https://github.com/camunda/camunda-platform-helm/commit/4cf435c5aa989eaab1b0dde9cbc75fb694774854))
* update minor-updates (minor) ([#4765](https://github.com/camunda/camunda-platform-helm/issues/4765)) ([54dc74d](https://github.com/camunda/camunda-platform-helm/commit/54dc74d5fed86702a26a63f247d7ccc25424946a))
* update module golang.org/x/crypto to v0.45.0 [security] ([#4745](https://github.com/camunda/camunda-platform-helm/issues/4745)) ([1b31ade](https://github.com/camunda/camunda-platform-helm/commit/1b31aded5d1e7297e9648ad2e225b86f716a3b3e))
* update module golang.org/x/oauth2 to v0.27.0 [security] ([#4731](https://github.com/camunda/camunda-platform-helm/issues/4731)) ([ee2f502](https://github.com/camunda/camunda-platform-helm/commit/ee2f5024283bc4ab1992ead7755387435e3bfcc3))
* update patch-updates ([#4761](https://github.com/camunda/camunda-platform-helm/issues/4761)) ([89f5551](https://github.com/camunda/camunda-platform-helm/commit/89f55518ddeaeec8fb0423afd173cd39e631ea95))
* update patch-updates (patch) ([#4762](https://github.com/camunda/camunda-platform-helm/issues/4762)) ([f8e7bbd](https://github.com/camunda/camunda-platform-helm/commit/f8e7bbd242097bb2c7c7bfde54aa53b3a5077af2))
* update patch-updates (patch) ([#4831](https://github.com/camunda/camunda-platform-helm/issues/4831)) ([c77bbe5](https://github.com/camunda/camunda-platform-helm/commit/c77bbe52c428f1a22597a76c19c0b26a40d6a8b7))
