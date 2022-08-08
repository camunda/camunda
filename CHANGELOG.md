# 8.1.0-alpha4

## 💊 Bugfixes

- **backend**: add healthy method to ElsIndicesCheck to check connection ([#1938](https://github.com/camunda/tasklist/issues/1938))
- **backend**: response with correct error code for identity auth err ([#1941](https://github.com/camunda/tasklist/issues/1941))
- prevent unclaim button from shrinking ([#1956](https://github.com/camunda/tasklist/issues/1956))
- Revert "fix(deps): update dependency @apollo/client to v3.6.2" ([#1936](https://github.com/camunda/tasklist/issues/1936))

## 🧹 Chore

- update Zeebe and Identity to 8.1.0-alpha4 ([#1967](https://github.com/camunda/tasklist/issues/1967))
- bump byte-buddy from 1.12.12 to 1.12.13 ([#1958](https://github.com/camunda/tasklist/issues/1958))
- Add possibility to skip backend tests ([#1959](https://github.com/camunda/tasklist/issues/1959))
- **preview-environment**: move to cheaper preemptible nodepool ([#1957](https://github.com/camunda/tasklist/issues/1957))
- bump mockito-core from 4.4.0 to 4.6.1 ([#1887](https://github.com/camunda/tasklist/issues/1887))
- **preview-environment**: migrate ingresses to new ingressClass ([#1953](https://github.com/camunda/tasklist/issues/1953))
- bump zeebe-test-container from 3.4.0 to 3.5.0 ([#1951](https://github.com/camunda/tasklist/issues/1951))
- bump netty-bom from 4.1.78.Final to 4.1.79.Final ([#1939](https://github.com/camunda/tasklist/issues/1939))
- bump version.micrometer from 1.9.1 to 1.9.2 ([#1940](https://github.com/camunda/tasklist/issues/1940))
- bump maven-assembly-plugin from 3.4.1 to 3.4.2 ([#1952](https://github.com/camunda/tasklist/issues/1952))
- bump exec-maven-plugin from 3.0.0 to 3.1.0 ([#1942](https://github.com/camunda/tasklist/issues/1942))
- Cleanup after Storybook removal
- **ci**: update GHA secrets to new Vault path ([#1944](https://github.com/camunda/tasklist/issues/1944))
- Remove Storybook and Chromatic ([#1949](https://github.com/camunda/tasklist/issues/1949))
- implement archiver as non-blocking ([#1932](https://github.com/camunda/tasklist/issues/1932))
- **gha**: adjust vault path for org secrets ([#1933](https://github.com/camunda/tasklist/issues/1933))
- **project**: use different artifact name for 1.3 ([#1934](https://github.com/camunda/tasklist/issues/1934))
- bump version.elasticsearch from 7.17.4 to 7.17.5 ([#1914](https://github.com/camunda/tasklist/issues/1914))
- bump maven-assembly-plugin from 3.3.0 to 3.4.1 ([#1931](https://github.com/camunda/tasklist/issues/1931))
- bump maven-failsafe-plugin from 3.0.0-M6 to 3.0.0-M7 ([#1889](https://github.com/camunda/tasklist/issues/1889))
- bump maven-surefire-plugin from 3.0.0-M6 to 3.0.0-M7 ([#1890](https://github.com/camunda/tasklist/issues/1890))
- bump maven-enforcer-plugin from 3.0.0 to 3.1.0 ([#1903](https://github.com/camunda/tasklist/issues/1903))
- bump version.micrometer from 1.9.0 to 1.9.1 ([#1906](https://github.com/camunda/tasklist/issues/1906))
- bump elasticsearch from 1.17.2 to 1.17.3 ([#1912](https://github.com/camunda/tasklist/issues/1912))
- bump version.log4j from 2.17.2 to 2.18.0 ([#1913](https://github.com/camunda/tasklist/issues/1913))
- **qa**: fix flaky test ([#1922](https://github.com/camunda/tasklist/issues/1922))

# v8.1.0-alpha3

## 💊 Bugfixes

- **backend**: use roles in organizations part of JWT ([#1916](https://github.com/camunda/tasklist/issues/1916))

## 🧹 Chore

- update Zeebe and Identity to 8.1.0-alpha3 ([#1920](https://github.com/camunda/tasklist/issues/1920))
- bump netty-bom from 4.1.77.Final to 4.1.78.Final ([#1904](https://github.com/camunda/tasklist/issues/1904))
- **project**: update issue template
- **TaskDetails**: align terminology ([#1877](https://github.com/camunda/tasklist/issues/1877))

# v8.1.0-alpha2

## 🚀 New Features

- Add salesPlanType and roles to User data ([#1888](https://github.com/camunda/tasklist/issues/1888))
- Pass JWT error message ([#1839](https://github.com/camunda/tasklist/issues/1839))

## 💊 Bugfixes

- save Identity authentication in persistent sessions ([#1813](https://github.com/camunda/tasklist/issues/1813))
- **backend**: add additional checks for error on migration ([#1828](https://github.com/camunda/tasklist/issues/1828))
- **deps**: update dependency graphql to v16.5.0 ([#1833](https://github.com/camunda/tasklist/issues/1833))
- **deps**: update dependency @carbon/react to v1.3.0 ([#1844](https://github.com/camunda/tasklist/issues/1844))
- **deps**: update all non-major dependencies ([#1826](https://github.com/camunda/tasklist/issues/1826))
- **deps**: update dependency @apollo/client to v3.6.2 ([#1469](https://github.com/camunda/tasklist/issues/1469))
- **deps**: update dependency mobx-react-lite to v3.4.0 ([#1824](https://github.com/camunda/tasklist/issues/1824))

## 🧹 Chore

- update Zeebe and Identity to 8.1.0-alpha2 ([#1892](https://github.com/camunda/tasklist/issues/1892))
- bump guava from 31.0.1-jre to 31.1-jre ([#1829](https://github.com/camunda/tasklist/issues/1829))
- **deps**: bump jib-maven-plugin from 3.2.0 to 3.2.1 ([#1831](https://github.com/camunda/tasklist/issues/1831))
- **deps**: bump zeebe-test-container from 3.3.0 to 3.4.0 ([#1842](https://github.com/camunda/tasklist/issues/1842))
- **deps**: bump version.micrometer from 1.8.5 to 1.9.0 ([#1871](https://github.com/camunda/tasklist/issues/1871))
- **deps**: bump assertj-core from 3.22.0 to 3.23.1 ([#1872](https://github.com/camunda/tasklist/issues/1872))
- **preview-env**: Keep using the branch name tagging ([#1873](https://github.com/camunda/tasklist/issues/1873))
- **preview-env**: move Tasklist preview environments docker images to Harbor from gcr.io ([#1866](https://github.com/camunda/tasklist/issues/1866))
- **deps**: bump version.micrometer from 1.8.5 to 1.9.0 ([#1841](https://github.com/camunda/tasklist/issues/1841))
- **deps**: bump version.jackson from 2.13.2 to 2.13.3 ([#1843](https://github.com/camunda/tasklist/issues/1843))
- **deps**: bump spring-session-bom from 2021.1.2 to 2021.2.0 ([#1855](https://github.com/camunda/tasklist/issues/1855))
- **deps**: bump elasticsearch from 1.16.3 to 1.17.2 ([#1859](https://github.com/camunda/tasklist/issues/1859))
- **deps**: bump version.elasticsearch from 7.17.3 to 7.17.4 ([#1864](https://github.com/camunda/tasklist/issues/1864))
- **deps**: update dependency husky to v8 ([#1827](https://github.com/camunda/tasklist/issues/1827))
- **deps**: update dependency @types/jest to v27.5.1 ([#1808](https://github.com/camunda/tasklist/issues/1808))
- **deps**: update dependency @types/node to v16.11.36 ([#1845](https://github.com/camunda/tasklist/issues/1845))
- **preview-env**: Use getSanitizedBranchName instead of creating a new function ([#1863](https://github.com/camunda/tasklist/issues/1863))
- **preview env**: fixing tear down gha to return a success
- **preview env**: resolve the namespace name limitation for branch deployment ([#1856](https://github.com/camunda/tasklist/issues/1856))
- **preview env**: cleanup old branch deployment Jobs ([#1851](https://github.com/camunda/tasklist/issues/1851))
- **preview env**: remove unneeded prefix
- **seed**: disable jobs on non prod env ([#1850](https://github.com/camunda/tasklist/issues/1850))
- **preview env**: Fix argocd app name
- **preview env**: Fix deploy GHA
- **preview env**: Fix tear down GHA
- **project**: rename camunda-cloud to camunda ([#1838](https://github.com/camunda/tasklist/issues/1838))
- **preview env**: cleanup old preview env files ([#1840](https://github.com/camunda/tasklist/issues/1840))
- **Jenkinsfile**: periodically trigger stable branches ([#1835](https://github.com/camunda/tasklist/issues/1835))
- **deps**: bump maven-failsafe-plugin from 3.0.0-M4 to 3.0.0-M6 ([#1789](https://github.com/camunda/tasklist/issues/1789))
- **deps**: bump version.elasticsearch from 7.17.2 to 7.17.3 ([#1804](https://github.com/camunda/tasklist/issues/1804))
- **deps**: bump maven-surefire-plugin from 3.0.0-M5 to 3.0.0-M6 ([#1805](https://github.com/camunda/tasklist/issues/1805))
- **deps**: bump nexus-staging-maven-plugin from 1.6.12 to 1.6.13 ([#1806](https://github.com/camunda/tasklist/issues/1806))
- bump maven-javadoc-plugin from 3.3.2 to 3.4.0 ([#1811](https://github.com/camunda/tasklist/issues/1811))
- bump netty-bom from 4.1.75.Final to 4.1.77.Final ([#1825](https://github.com/camunda/tasklist/issues/1825))

# v8.1.0-alpha1

## 🚀 New Features

- **ci**: migrate frontend stages to github actions ([#1624](https://github.com/camunda-cloud/tasklist/issues/1624))

## 💊 Bugfixes

- add SSOConfigurator to avoid circular refs ([#1816](https://github.com/camunda-cloud/tasklist/issues/1816))
- **deps**: update all non-major dependencies ([#1775](https://github.com/camunda-cloud/tasklist/issues/1775))
- **deps**: update dependency @carbon/react to v1.2.0 ([#1809](https://github.com/camunda-cloud/tasklist/issues/1809))
- **deps**: update dependency sass to v1.51.0 ([#1796](https://github.com/camunda-cloud/tasklist/issues/1796))
- fix SNAPSHOT docker image creation ([#1800](https://github.com/camunda-cloud/tasklist/issues/1800))
- **deps**: update dependency graphql to v16.4.0 ([#1792](https://github.com/camunda-cloud/tasklist/issues/1792))
- **deps**: update dependency @carbon/react to v1.1.0 ([#1777](https://github.com/camunda-cloud/tasklist/issues/1777))
- Add bumpLabels to defaultChangelogConfig ([#1782](https://github.com/camunda-cloud/tasklist/issues/1782))
- **deps**: update dependency sass to v1.50.0 ([#1773](https://github.com/camunda-cloud/tasklist/issues/1773))
- **deps**: update dependency polished to v4.2.2 ([#1772](https://github.com/camunda-cloud/tasklist/issues/1772))
- **deps**: update dependency react-router-dom to v6.3.0 ([#1742](https://github.com/camunda-cloud/tasklist/issues/1742))
- **deps**: update all non-major dependencies ([#1741](https://github.com/camunda-cloud/tasklist/issues/1741))
- **deps**: update dependency @carbon/react to v1 ([#1748](https://github.com/camunda-cloud/tasklist/issues/1748))

## 🧹 Chore

- update Zeebe and Identity to 8.1.0-alpha1 ([#1817](https://github.com/camunda-cloud/tasklist/issues/1817))
- bump version.spring.boot from 2.5.12 to 2.6.7 ([#1798](https://github.com/camunda-cloud/tasklist/issues/1798))
- bump version.spring.boot from 2.5.5 to 2.6.6 ([#1753](https://github.com/camunda-cloud/tasklist/issues/1753))
- **deps**: update dependency zeebe-node to v8 ([#1810](https://github.com/camunda-cloud/tasklist/issues/1810))
- **deps**: update node.js to v16.15.0 ([#1776](https://github.com/camunda-cloud/tasklist/issues/1776))
- **deps**: update dependency lint-staged to v12.4.1 ([#1791](https://github.com/camunda-cloud/tasklist/issues/1791))
- **deps-dev**: bump zeebe-test-container from 3.2.0 to 3.3.0 ([#1598](https://github.com/camunda-cloud/tasklist/issues/1598))
- **deps**: bump version.log4j from 2.17.1 to 2.17.2 ([#1653](https://github.com/camunda-cloud/tasklist/issues/1653))
- **deps-dev**: bump docker-java-core from 3.2.12 to 3.2.13 ([#1654](https://github.com/camunda-cloud/tasklist/issues/1654))
- **deps**: bump version.micrometer from 1.8.2 to 1.8.5 ([#1797](https://github.com/camunda-cloud/tasklist/issues/1797))
- adjust user assertion ([#1802](https://github.com/camunda-cloud/tasklist/issues/1802))
- Build frontend together with backend on Jenkins ([#1801](https://github.com/camunda-cloud/tasklist/issues/1801))
- **deps**: bump version.elasticsearch from 7.16.3 to 7.17.2 ([#1745](https://github.com/camunda-cloud/tasklist/issues/1745))
- **deps**: bump mvc-auth-commons from 1.8.2 to 1.9.2 ([#1780](https://github.com/camunda-cloud/tasklist/issues/1780))
- fix usage metric test ([#1795](https://github.com/camunda-cloud/tasklist/issues/1795))
- adjust backend after minor 8.0.0 ([#1771](https://github.com/camunda-cloud/tasklist/issues/1771))
- fix metrics tests ([#1794](https://github.com/camunda-cloud/tasklist/issues/1794))
- Remove getting started experience support ([#1788](https://github.com/camunda-cloud/tasklist/issues/1788))
- **deps**: update dependency @types/carbon-components-react to v7.55.1 ([#1778](https://github.com/camunda-cloud/tasklist/issues/1778))
- **deps**: bump maven-shade-plugin from 3.2.4 to 3.3.0 ([#1746](https://github.com/camunda-cloud/tasklist/issues/1746))
- update dependency zeebe-node to v2 ([#1573](https://github.com/camunda-cloud/tasklist/issues/1573))
- create changelog workflows ([#1764](https://github.com/camunda-cloud/tasklist/issues/1764))
- **deps**: update definitelytyped (major) ([#1774](https://github.com/camunda-cloud/tasklist/issues/1774))
- **deps**: update dependency jest-junit to v13.1.0 ([#1767](https://github.com/camunda-cloud/tasklist/issues/1767))
