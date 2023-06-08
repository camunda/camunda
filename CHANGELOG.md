# v8.3.0-alpha2
## 🚀 New Features
* support draft variables ([#3009](https://github.com/camunda/tasklist/issues/3009))
* Add success and error messages to public start page ([#3032](https://github.com/camunda/tasklist/issues/3032))
* Implement form submission on public form start ([#3018](https://github.com/camunda/tasklist/issues/3018))
* changing from processDefinitionKey to bpmnProcessId ([#3016](https://github.com/camunda/tasklist/issues/3016))
* Fetch public start form ([#3004](https://github.com/camunda/tasklist/issues/3004))
* new endpoint filter publicEndpoints ([#2980](https://github.com/camunda/tasklist/issues/2980))
* adding title to /v1/external/processes/{bpmnProcessId}/form ([#3003](https://github.com/camunda/tasklist/issues/3003))
* adding feature flag for start process from form ([#2929](https://github.com/camunda/tasklist/issues/2929))
* Signal assignee on task detail ([#2946](https://github.com/camunda/tasklist/issues/2946))
* adding /v1/external/process/{processDefinitionKey}/start ([#2927](https://github.com/camunda/tasklist/issues/2927))
* Create start process from form page  ([#2939](https://github.com/camunda/tasklist/issues/2939))
* adding /v1/internal/process/publicEndpoints ([#2917](https://github.com/camunda/tasklist/issues/2917))
* adding /v1/external/process/{bpmnProcessId}/form ([#2910](https://github.com/camunda/tasklist/issues/2910))
* adding new dev-data to cover start process form ([#2915](https://github.com/camunda/tasklist/issues/2915))
* adding new fields for start process from form ([#2890](https://github.com/camunda/tasklist/issues/2890))
* import fields from Zeebe to start Process by Form ([#2891](https://github.com/camunda/tasklist/issues/2891))

## 💊 Bugfixes
* Sort completed tasks by completion ([#3055](https://github.com/camunda/tasklist/issues/3055))
* Show ellipsis for long texts on left panel ([#3057](https://github.com/camunda/tasklist/issues/3057))
* Use fixed submit button ([#3047](https://github.com/camunda/tasklist/issues/3047))
* Update header title and remove user settings menu ([#3044](https://github.com/camunda/tasklist/issues/3044))
* disabling introspection for any environment that is not dev ([#3060](https://github.com/camunda/tasklist/issues/3060))
* **deps**: update all non-major dependencies to v1.0.0-alpha.7 ([#3053](https://github.com/camunda/tasklist/issues/3053))
* Fix E2E tests
* **deps**: update all non-major dependencies to v1.0.0-alpha.6 ([#3045](https://github.com/camunda/tasklist/issues/3045))
* **deps**: update all non-major dependencies to v1.0.0-alpha.5 ([#3041](https://github.com/camunda/tasklist/issues/3041))
* error when submitting form to start a process ([#3040](https://github.com/camunda/tasklist/issues/3040))
* **deps**: update all non-major dependencies to v1.0.0-alpha.1 ([#3025](https://github.com/camunda/tasklist/issues/3025))
* Fix full variable fetch ([#3024](https://github.com/camunda/tasklist/issues/3024))
* reset values for truncated variables (to master) ([#3008](https://github.com/camunda/tasklist/issues/3008))
* **deps**: update dependency @carbon/react to v1.30.0 ([#3012](https://github.com/camunda/tasklist/issues/3012))
* **deps**: update dependency @carbon/elements to v11.23.0 ([#3011](https://github.com/camunda/tasklist/issues/3011))
* **deps**: update all non-major dependencies ([#3014](https://github.com/camunda/tasklist/issues/3014))
* **deps**: update dependency styled-components to v5.3.11 ([#3007](https://github.com/camunda/tasklist/issues/3007))
* non-required form fields not working to complete task ([#2997](https://github.com/camunda/tasklist/issues/2997))
* worker updating task when timed_out ([#2964](https://github.com/camunda/tasklist/issues/2964))
* Json logs are not working ([#2962](https://github.com/camunda/tasklist/issues/2962))
* **deps**: update dependency react-router-dom to v6.11.2 ([#2959](https://github.com/camunda/tasklist/issues/2959))
* **deps**: update all non-major dependencies ([#2935](https://github.com/camunda/tasklist/issues/2935))
* **deps**: update all non-major dependencies ([#2932](https://github.com/camunda/tasklist/issues/2932))
* **deps**: update dependency @carbon/react to v1.29.1 ([#2931](https://github.com/camunda/tasklist/issues/2931))
* deploy-preview ([#2922](https://github.com/camunda/tasklist/issues/2922))
* **deps**: update dependency @camunda/camunda-composite-components to v0.0.39 ([#2920](https://github.com/camunda/tasklist/issues/2920))
* **deps**: update all non-major dependencies ([#2912](https://github.com/camunda/tasklist/issues/2912))
* **deps**: update all non-major dependencies ([#2815](https://github.com/camunda/tasklist/issues/2815))
* **deps**: update dependency react-router-dom to v6.11.1 ([#2855](https://github.com/camunda/tasklist/issues/2855))
* **deps**: update dependency @apollo/client to v3.7.14 ([#2851](https://github.com/camunda/tasklist/issues/2851))
* **deps**: update dependency @carbon/elements to v11.22.0 ([#2852](https://github.com/camunda/tasklist/issues/2852))
* **deps**: update dependency mixpanel-browser to v2.47.0 ([#2854](https://github.com/camunda/tasklist/issues/2854))
* **deps**: update dependency date-fns to v2.30.0 ([#2856](https://github.com/camunda/tasklist/issues/2856))

## 🧹 Chore
* **backend**: bumping Zeebe/Identity versions for release ([#3065](https://github.com/camunda/tasklist/issues/3065))
* Skip flaky E2E test
* **deps**: update actions/add-to-project digest to 65dd57f ([#3058](https://github.com/camunda/tasklist/issues/3058))
* fix backup/restore test ([#3052](https://github.com/camunda/tasklist/issues/3052))
* **deps**: update dependency monaco-editor to v0.39.0 ([#3054](https://github.com/camunda/tasklist/issues/3054))
* Increase scrolling timeout
* Improve E2E assertion mistakes
* Task Panel tests migrated from TestCafe to Playwright ([#3021](https://github.com/camunda/tasklist/issues/3021))
* **deps**: update dependency typescript to v5.1.3 ([#3033](https://github.com/camunda/tasklist/issues/3033))
* **sync-issues**: Add a workflow to make public copies of the issues ([#3031](https://github.com/camunda/tasklist/issues/3031))
* **deps**: update all non-major dependencies ([#3034](https://github.com/camunda/tasklist/issues/3034))
* **deps**: update actions/add-to-project digest to 4168cde ([#3030](https://github.com/camunda/tasklist/issues/3030))
* **deps**: update dependency testcafe to v2.6.2 ([#3028](https://github.com/camunda/tasklist/issues/3028))
* Add tracking to public start form page ([#3022](https://github.com/camunda/tasklist/issues/3022))
* **deps**: update all non-major dependencies ([#3020](https://github.com/camunda/tasklist/issues/3020))
* Add package manager version to package.json
* Remove Apollo client
* **deps**: update actions/add-to-project digest to 5ba1147 ([#3013](https://github.com/camunda/tasklist/issues/3013))
* Remove Apollo and GraphQL ([#2988](https://github.com/camunda/tasklist/issues/2988))
* Use REST API endpoints for current user request ([#2987](https://github.com/camunda/tasklist/issues/2987))
* Use REST API task endpoints ([#2985](https://github.com/camunda/tasklist/issues/2985))
* Login test migrated ([#3000](https://github.com/camunda/tasklist/issues/3000))
* **deps**: update all non-major dependencies ([#3006](https://github.com/camunda/tasklist/issues/3006))
* **deps**: update dependency axe-core to v4.7.2 ([#3005](https://github.com/camunda/tasklist/issues/3005))
* Update form-js ([#2996](https://github.com/camunda/tasklist/issues/2996))
* Prepare for E2E Playwright migration ([#2977](https://github.com/camunda/tasklist/issues/2977))
* Update Playwright image version
* **deps**: update all non-major dependencies to v1.34.3 ([#2994](https://github.com/camunda/tasklist/issues/2994))
* **deps**: update actions/add-to-project digest to 588a05e ([#2993](https://github.com/camunda/tasklist/issues/2993))
* **deps**: update dependency @types/react to v18.2.7 ([#2992](https://github.com/camunda/tasklist/issues/2992))
* **deps**: update actions/add-to-project digest to 1b04211 ([#2991](https://github.com/camunda/tasklist/issues/2991))
* **deps**: update all non-major dependencies to v1.34.2 ([#2983](https://github.com/camunda/tasklist/issues/2983))
* **deps**: update hashicorp/vault-action digest to 2d9c2b9 ([#2982](https://github.com/camunda/tasklist/issues/2982))
* **deps**: update helm release camunda-platform to v8.2.4 ([#2981](https://github.com/camunda/tasklist/issues/2981))
* **e2e**: update form-js-integration screenshot ([#2978](https://github.com/camunda/tasklist/issues/2978))
* **deps**: update all non-major dependencies to v1.34.1 ([#2975](https://github.com/camunda/tasklist/issues/2975))
* **deps**: update dependency @types/node to v18.16.14 ([#2970](https://github.com/camunda/tasklist/issues/2970))
* **deps**: update hashicorp/vault-action digest to d27529e ([#2967](https://github.com/camunda/tasklist/issues/2967))
* **deps**: update dependency @playwright/test to v1.34.0 ([#2968](https://github.com/camunda/tasklist/issues/2968))
* **deps**: update dependency @types/node to v18.16.13 ([#2963](https://github.com/camunda/tasklist/issues/2963))
* **deps**: update dependency @axe-core/playwright to v4.7.1 ([#2949](https://github.com/camunda/tasklist/issues/2949))
* **deps**: update dependency @types/node to v18.16.12 ([#2948](https://github.com/camunda/tasklist/issues/2948))
* **deps**: update camunda/zeebe docker tag to v8.2.5 ([#2947](https://github.com/camunda/tasklist/issues/2947))
* **deps**: update dependency @types/node to v18.16.10 ([#2943](https://github.com/camunda/tasklist/issues/2943))
* bump version.elasticsearch from 7.17.9 to 7.17.10 ([#2899](https://github.com/camunda/tasklist/issues/2899))
* Use REST endpoints on processes page ([#2926](https://github.com/camunda/tasklist/issues/2926))
* **deps**: update actions/add-to-project digest to c3dbb76 ([#2930](https://github.com/camunda/tasklist/issues/2930))
* **deps**: update dependency @types/node to v18.16.8 ([#2925](https://github.com/camunda/tasklist/issues/2925))
* **deps**: update helm release camunda-platform to v8.2.3 ([#2924](https://github.com/camunda/tasklist/issues/2924))
* **deps**: update dependency testcafe to v2.6.0 ([#2921](https://github.com/camunda/tasklist/issues/2921))
* Add documentation about E2E tests ([#2919](https://github.com/camunda/tasklist/issues/2919))
* Add REST API endpoints ([#2913](https://github.com/camunda/tasklist/issues/2913))
* **deps**: update actions/add-to-project digest to f52c62c ([#2911](https://github.com/camunda/tasklist/issues/2911))
* Extract request function and make it more generic ([#2906](https://github.com/camunda/tasklist/issues/2906))
* updating minor version for spring boot ([#2905](https://github.com/camunda/tasklist/issues/2905))
* Remove React Query lazy loading
* **deps**: update dependency @types/node to v18.16.6 ([#2903](https://github.com/camunda/tasklist/issues/2903))
* Fix branch name
* Update form-js visual regression integration test screenshot
* **deps**: update helm release camunda-platform to v8.2.2 ([#2902](https://github.com/camunda/tasklist/issues/2902))
* **deps**: update actions/add-to-project digest to bde621f ([#2901](https://github.com/camunda/tasklist/issues/2901))
* Add setup for form-js visual integration test  ([#2869](https://github.com/camunda/tasklist/issues/2869))
* **deps**: update dependency monaco-editor to v0.38.0 ([#2859](https://github.com/camunda/tasklist/issues/2859))
* bump testcontainers-keycloak from 1.10.0 to 2.5.0 ([#2836](https://github.com/camunda/tasklist/issues/2836))
* **deps**: bump byte-buddy from 1.12.22 to 1.14.4 ([#2752](https://github.com/camunda/tasklist/issues/2752))
* **deps**: bump netty-bom from 4.1.87.Final to 4.1.92.Final ([#2828](https://github.com/camunda/tasklist/issues/2828))
* bump maven-assembly-plugin from 3.4.2 to 3.5.0 ([#2835](https://github.com/camunda/tasklist/issues/2835))
* **els/session**: remove exists requests ([#2882](https://github.com/camunda/tasklist/issues/2882))
* bump maven-release-plugin from 2.5.3 to 3.0.0 ([#2880](https://github.com/camunda/tasklist/issues/2880))
* update CHANGELOG.md
# v8.3.0-alpha1
## 🚀 New Features
* Consume form-js Carbonisation ([#2872](https://github.com/camunda/tasklist/issues/2872))
* Enable processes menu item on all saas versions ([#2833](https://github.com/camunda/tasklist/issues/2833))
* re-enabling processes for saas ([#2840](https://github.com/camunda/tasklist/issues/2840))
* hide completion and add variable button completed tasks ([#2802](https://github.com/camunda/tasklist/issues/2802))
* **backend**: consume tasklist identity `redirect_root_url` from env variables ([#2739](https://github.com/camunda/tasklist/issues/2739))

## 💊 Bugfixes
* **backend**: process duplications properly ([#2825](https://github.com/camunda/tasklist/issues/2825))
* Fix labels ([#2817](https://github.com/camunda/tasklist/issues/2817))
* changing dateformat to accept zoned ([#2807](https://github.com/camunda/tasklist/issues/2807))
* add `alt` property to images ([#2810](https://github.com/camunda/tasklist/issues/2810))
* **deps**: update dependency @apollo/client to v3.7.12 ([#2724](https://github.com/camunda/tasklist/issues/2724))
* **deps**: update all non-major dependencies ([#2709](https://github.com/camunda/tasklist/issues/2709))
* **deps**: update dependency @monaco-editor/react to v4.5.0 ([#2755](https://github.com/camunda/tasklist/issues/2755))
* **deps**: update dependency @carbon/react to v1.27.0 ([#2794](https://github.com/camunda/tasklist/issues/2794))
* **deps**: update dependency @carbon/elements to v11.21.0 ([#2793](https://github.com/camunda/tasklist/issues/2793))
* authentication rest api ([#2796](https://github.com/camunda/tasklist/issues/2796))
* Fix radio button disabled state style ([#2781](https://github.com/camunda/tasklist/issues/2781))
* **deps**: update dependency sass to v1.62.0 ([#2754](https://github.com/camunda/tasklist/issues/2754))
* adding identity check ([#2763](https://github.com/camunda/tasklist/issues/2763))
* adding extra exception for identity configuration ([#2761](https://github.com/camunda/tasklist/issues/2761))
* identity resources enabled ([#2760](https://github.com/camunda/tasklist/issues/2760))
* **deps**: update dependency @carbon/react to v1.26.0 ([#2722](https://github.com/camunda/tasklist/issues/2722))

## 🧹 Chore
* **els/repo**: remove not needed refresh when getting session ([#2875](https://github.com/camunda/tasklist/issues/2875))
* updating dependencies to 8.3.0-alpha1 ([#2878](https://github.com/camunda/tasklist/issues/2878))
* Make visual regression tests more reliable
* **deps**: update actions/add-to-project digest to 23e1389 ([#2868](https://github.com/camunda/tasklist/issues/2868))
* **deps**: update dependency @axe-core/playwright to v4.7.0 ([#2858](https://github.com/camunda/tasklist/issues/2858))
* Add React Query ([#2850](https://github.com/camunda/tasklist/issues/2850))
* Add strict mode back
* **deps**: update actions/add-to-project digest to 87685c7 ([#2841](https://github.com/camunda/tasklist/issues/2841))
* **deps**: update mcr.microsoft.com/playwright docker tag to v1.33.0 ([#2843](https://github.com/camunda/tasklist/issues/2843))
* removing hard-coded identity version on tests ([#2829](https://github.com/camunda/tasklist/issues/2829))
* **deps**: update definitelytyped ([#2830](https://github.com/camunda/tasklist/issues/2830))
* bump version.elasticsearch from 7.17.7 to 7.17.9 ([#2465](https://github.com/camunda/tasklist/issues/2465))
* bump version.jackson from 2.14.1 to 2.14.2 ([#2442](https://github.com/camunda/tasklist/issues/2442))
* Simplify Playwright CI ([#2827](https://github.com/camunda/tasklist/issues/2827))
* **deps**: update dependency @types/node to v18.16.0 ([#2819](https://github.com/camunda/tasklist/issues/2819))
* **deps**: update actions/add-to-project digest to 25f81e7 ([#2822](https://github.com/camunda/tasklist/issues/2822))
* Update form-js to 0.14.1 ([#2818](https://github.com/camunda/tasklist/issues/2818))
* **deps**: update actions/add-to-project digest to 0a7abac ([#2814](https://github.com/camunda/tasklist/issues/2814))
* adding log4j2.xml file to docker image ([#2805](https://github.com/camunda/tasklist/issues/2805))
* Update Vault action version
* **deps**: update dependency msw to v1 ([#2559](https://github.com/camunda/tasklist/issues/2559))
* **deps**: update dependency @types/node to v18.15.12 ([#2809](https://github.com/camunda/tasklist/issues/2809))
* Revert to supported mobx-react-lite verison
* update after minor release 8.2 ([#2790](https://github.com/camunda/tasklist/issues/2790))
* **deps**: update actions/add-to-project digest to 6319fbf ([#2803](https://github.com/camunda/tasklist/issues/2803))
* **deps**: update dependency axe-core to v4.7.0 ([#2798](https://github.com/camunda/tasklist/issues/2798))
* add instruction on how to run app using IntelliJ IDEA ([#2784](https://github.com/camunda/tasklist/issues/2784))
* **coverage**: setup test code coverage ([#2767](https://github.com/camunda/tasklist/issues/2767))
* Add missing visual regression tests ([#2785](https://github.com/camunda/tasklist/issues/2785))
* Mock version on visual regression tests ([#2782](https://github.com/camunda/tasklist/issues/2782))
* **deps**: update dependency monaco-editor to v0.37.1 ([#2738](https://github.com/camunda/tasklist/issues/2738))
* Document how to inspect visual regression CI failures ([#2772](https://github.com/camunda/tasklist/issues/2772))
* **deps**: update dependency testcafe to v2.5.0 ([#2753](https://github.com/camunda/tasklist/issues/2753))
* **deps**: update actions/add-to-project digest to 16678f0 ([#2756](https://github.com/camunda/tasklist/issues/2756))
* **deps**: update helm release camunda-platform to v8.2.0 ([#2766](https://github.com/camunda/tasklist/issues/2766))
* **deps**: update hashicorp/vault-action digest to 1d767e3 ([#2725](https://github.com/camunda/tasklist/issues/2725))
* **deps**: update actions/add-to-project digest to 80dff83 ([#2714](https://github.com/camunda/tasklist/issues/2714))
* update CHANGELOG.md
# v8.2.0
## 🚀 New Features
* adding flag RESOURCE_PERMISSIONS_ENABLED ([#2727](https://github.com/camunda/tasklist/issues/2727))
* Enable processes tab for self managed users ([#2716](https://github.com/camunda/tasklist/issues/2716))
* Remove feature flag (Enable follow up and due dates and candidate groups) ([#2701](https://github.com/camunda/tasklist/issues/2701))
* tasks REST API ([#2583](https://github.com/camunda/tasklist/issues/2583))
* GetProcesses returning based on Identity ([#2671](https://github.com/camunda/tasklist/issues/2671))
* **backend**: add ILM deletion policy for archived indices ([#2679](https://github.com/camunda/tasklist/issues/2679))
* Remove claimed by me wording ([#2683](https://github.com/camunda/tasklist/issues/2683))
* Implement new details layout ([#2674](https://github.com/camunda/tasklist/issues/2674))
* Due and Follow-up Dates ([#2664](https://github.com/camunda/tasklist/issues/2664))
* Update alpha feedback link ([#2659](https://github.com/camunda/tasklist/issues/2659))
* Candidate Users ([#2640](https://github.com/camunda/tasklist/issues/2640))
* Add sorting button to left panel ([#2578](https://github.com/camunda/tasklist/issues/2578))

## 💊 Bugfixes
* **backend**: upgrade `springdoc` to support SpringBoot v3 ([#2742](https://github.com/camunda/tasklist/issues/2742))
* Use image tags that exist ([#2741](https://github.com/camunda/tasklist/issues/2741))
* Fix overflow form-js overflow on small screens ([#2733](https://github.com/camunda/tasklist/issues/2733))
* versions for TaskTemplate ([#2729](https://github.com/camunda/tasklist/issues/2729))
* fixing failing tests on master ([#2730](https://github.com/camunda/tasklist/issues/2730))
* when no BaseURL is configured is not possible to reach identity ([#2723](https://github.com/camunda/tasklist/issues/2723))
* fix broken Unit tests ([#2717](https://github.com/camunda/tasklist/issues/2717))
* migration tests failing ([#2715](https://github.com/camunda/tasklist/issues/2715))
* fixing scripts ([#2713](https://github.com/camunda/tasklist/issues/2713))
* fixing ascending ordenation ([#2712](https://github.com/camunda/tasklist/issues/2712))
* **deps**: update dependency @bpmn-io/form-js-viewer to v0.13.1 ([#2706](https://github.com/camunda/tasklist/issues/2706))
* **deps**: update dependency react-router-dom to v6.10.0 ([#2708](https://github.com/camunda/tasklist/issues/2708))
* sorting order using before and after ([#2705](https://github.com/camunda/tasklist/issues/2705))
* **auth**: Avoid NullPointerException when storing session in ELS ([#2703](https://github.com/camunda/tasklist/issues/2703))
* **sso**: Add app name connectors to cluster metadata ([#2702](https://github.com/camunda/tasklist/issues/2702))
* **backend**: change type of backupId to Integer ([#2680](https://github.com/camunda/tasklist/issues/2680))
* due and follow-up dates null indexation ([#2700](https://github.com/camunda/tasklist/issues/2700))
* **deps**: update all non-major dependencies ([#2681](https://github.com/camunda/tasklist/issues/2681))
* **deps**: update dependency mobx-react-lite to v4 ([#2685](https://github.com/camunda/tasklist/issues/2685))
* **deps**: update dependency mobx to v6.9.0 ([#2684](https://github.com/camunda/tasklist/issues/2684))
* **deps**: update all non-major dependencies ([#2663](https://github.com/camunda/tasklist/issues/2663))
* **deps**: update dependency sass to v1.60.0 ([#2672](https://github.com/camunda/tasklist/issues/2672))
* **deps**: update dependency mixpanel-browser to v2.46.0 ([#2673](https://github.com/camunda/tasklist/issues/2673))
* zeebe endpoints with / in the end don't work after spring version upgrade ([#2660](https://github.com/camunda/tasklist/issues/2660))
* Date time calendar columns ([#2650](https://github.com/camunda/tasklist/issues/2650))
* **deps**: update dependency @carbon/react to v1.25.0 ([#2648](https://github.com/camunda/tasklist/issues/2648))
* **deps**: update all non-major dependencies ([#2630](https://github.com/camunda/tasklist/issues/2630))
* **deps**: update dependency zod to v3.21.4 ([#2632](https://github.com/camunda/tasklist/issues/2632))
* Replace community Slack link ([#2623](https://github.com/camunda/tasklist/issues/2623))
* **deps**: update dependency @carbon/react to v1.24.0 ([#2588](https://github.com/camunda/tasklist/issues/2588))
* **deps**: update dependency @carbon/elements to v11.20.0 ([#2587](https://github.com/camunda/tasklist/issues/2587))
* **deps**: update dependency react-router-dom to v6.9.0 ([#2618](https://github.com/camunda/tasklist/issues/2618))
* **deps**: update dependency sass to v1.59.2 ([#2619](https://github.com/camunda/tasklist/issues/2619))
* **chore**: fix apt-get cmdline
* **chore**: fix apt-get cmdline
* **chore**: add git config ([#2608](https://github.com/camunda/tasklist/issues/2608))

## 🧹 Chore
* fixing docker version for zeebe ([#2745](https://github.com/camunda/tasklist/issues/2745))
* Update form-js to 0.14.0 ([#2743](https://github.com/camunda/tasklist/issues/2743))
* **backend**: update Zeebe and Identity to 8.2.0 ([#2740](https://github.com/camunda/tasklist/issues/2740))
* **deps**: Update Spring Boot version to 3.0.5 ([#2698](https://github.com/camunda/tasklist/issues/2698))
* Update Zeebe ([#2707](https://github.com/camunda/tasklist/issues/2707))
* **deps**: update all non-major dependencies ([#2689](https://github.com/camunda/tasklist/issues/2689))
* **deps**: update dependency zeebe-node to v8.2.0 ([#2691](https://github.com/camunda/tasklist/issues/2691))
* **deps**: update hashicorp/vault-action digest to c253c15 ([#2697](https://github.com/camunda/tasklist/issues/2697))
* Add tracking for sorting ([#2682](https://github.com/camunda/tasklist/issues/2682))
* Update mockServiceWorker format
* **deps**: update dependency @playwright/test to v1.32.0 ([#2661](https://github.com/camunda/tasklist/issues/2661))
* **deps**: update dlavrenuek/conventional-changelog-action action to v1.2.3 ([#2658](https://github.com/camunda/tasklist/issues/2658))
* Remove unused dep
* Remove unused dep
* **deps**: update dependency prettier to v2.8.6 ([#2657](https://github.com/camunda/tasklist/issues/2657))
* **deps**: update all non-major dependencies ([#2652](https://github.com/camunda/tasklist/issues/2652))
* **deps**: update actions/add-to-project digest to e78e561 ([#2654](https://github.com/camunda/tasklist/issues/2654))
* **deps**: update dependency eslint-config-prettier to v8.8.0 ([#2655](https://github.com/camunda/tasklist/issues/2655))
* **deps**: update dependency prettier to v2.8.5 ([#2649](https://github.com/camunda/tasklist/issues/2649))
* **deps**: update dependency @types/jest to v29.5.0 ([#2645](https://github.com/camunda/tasklist/issues/2645))
* **deps**: update dependency typescript to v5 ([#2646](https://github.com/camunda/tasklist/issues/2646))
* add a11y integration test ([#2439](https://github.com/camunda/tasklist/issues/2439))
* Fix tests
* Remove unnecessary ignores from eslint
* Make eslint config more general
* Fix prettier linting on eslint
* Split instance creation for E2E tests ([#2628](https://github.com/camunda/tasklist/issues/2628))
* adding OCI and OpenShift labels ([#2602](https://github.com/camunda/tasklist/issues/2602))
* **deps**: update dependency lint-staged to v13.2.0 ([#2613](https://github.com/camunda/tasklist/issues/2613))
* **deps**: update dependency @types/node to v18.15.1 ([#2609](https://github.com/camunda/tasklist/issues/2609))
* **deps**: update actions/add-to-project digest to 097fa05 ([#2616](https://github.com/camunda/tasklist/issues/2616))
* **deps**: update all non-major dependencies ([#2582](https://github.com/camunda/tasklist/issues/2582))
* **deps**: update dependency eslint-config-prettier to v8.7.0 ([#2590](https://github.com/camunda/tasklist/issues/2590))
* update CHANGELOG.md
# v8.2.0-alpha5
## 🚀 New Features
* **backend**: use sequence field for import ([#2512](https://github.com/camunda/tasklist/issues/2512))
* improve error handling when invalid auth token provided ([#2591](https://github.com/camunda/tasklist/issues/2591))
* Update left panel layout ([#2545](https://github.com/camunda/tasklist/issues/2545))
* **backend**: use Elasticsearch client compatibility mode to run on Elasticsearch 8 ([#2510](https://github.com/camunda/tasklist/issues/2510))
* increase batch size ([#2484](https://github.com/camunda/tasklist/issues/2484))
* use runtime indexes for searching CREATED tasks ([#2476](https://github.com/camunda/tasklist/issues/2476))

## 💊 Bugfixes
* invalid operate token ([#2571](https://github.com/camunda/tasklist/issues/2571))
* **deps**: update dependency @apollo/client to v3.7.10 ([#2593](https://github.com/camunda/tasklist/issues/2593))
* **deps**: update dependency @camunda/camunda-composite-components to v0.0.30 ([#2575](https://github.com/camunda/tasklist/issues/2575))
* **deps**: update dependency react-router-dom to v6.8.2 ([#2562](https://github.com/camunda/tasklist/issues/2562))
* Add new message for readonly users ([#2538](https://github.com/camunda/tasklist/issues/2538))
* **deps**: update all non-major dependencies ([#2499](https://github.com/camunda/tasklist/issues/2499))
* upgrade-insecure-requests ([#2542](https://github.com/camunda/tasklist/issues/2542))
* **deps**: update dependency @carbon/react to v1.23.0 ([#2520](https://github.com/camunda/tasklist/issues/2520))
* Fix empty assignee field for restricted users ([#2526](https://github.com/camunda/tasklist/issues/2526))
* resolve GraphQL vulnerability ([#2509](https://github.com/camunda/tasklist/issues/2509))
* get processes returning duplicated processes ([#2514](https://github.com/camunda/tasklist/issues/2514))
* fix vulnerability issues [snakeyaml] ([#2488](https://github.com/camunda/tasklist/issues/2488))
* Fix form-js placeholder color ([#2511](https://github.com/camunda/tasklist/issues/2511))
* **deps**: update dependency mobx to v6.8.0 ([#2497](https://github.com/camunda/tasklist/issues/2497))

## 🧹 Chore
* **backend**: update Zeebe and Identity to 8.2.0-alpha5
* **backend**: update Zeebe and Identity to 8.2.0-alpha5 ([#2605](https://github.com/camunda/tasklist/issues/2605))
* **deps**: update dependency testcafe to v2.4.0 ([#2596](https://github.com/camunda/tasklist/issues/2596))
* Adding how to build and run tasklist ([#2518](https://github.com/camunda/tasklist/issues/2518))
* Preload main fonts ([#2579](https://github.com/camunda/tasklist/issues/2579))
* **deps**: update hashicorp/vault-action digest to 3a9100e ([#2581](https://github.com/camunda/tasklist/issues/2581))
* **pom**: fix Maven filtering ([#2577](https://github.com/camunda/tasklist/issues/2577))
* **deps**: update all non-major dependencies ([#2573](https://github.com/camunda/tasklist/issues/2573))
* **deps**: update hashicorp/vault-action digest to 3bbbc68 ([#2570](https://github.com/camunda/tasklist/issues/2570))
* **deps**: update hashicorp/vault-action digest to 74bc2a6 ([#2565](https://github.com/camunda/tasklist/issues/2565))
* **deps**: update dependency monaco-editor to v0.36.1 ([#2564](https://github.com/camunda/tasklist/issues/2564))
* Remove custom JSDOM
* **deps**: update hashicorp/vault-action digest to 76780d4 ([#2563](https://github.com/camunda/tasklist/issues/2563))
* Make transition when changing filters more smooth ([#2544](https://github.com/camunda/tasklist/issues/2544))
* **deps**: update actions/add-to-project digest to 4756e63 ([#2560](https://github.com/camunda/tasklist/issues/2560))
* **deps**: update all non-major dependencies ([#2543](https://github.com/camunda/tasklist/issues/2543))
* **deps**: update dependency monaco-editor to v0.36.0 ([#2558](https://github.com/camunda/tasklist/issues/2558))
* Update React, CRA and testing library deps ([#2537](https://github.com/camunda/tasklist/issues/2537))
* **deps**: update dependency @playwright/test to v1.31.0 ([#2540](https://github.com/camunda/tasklist/issues/2540))
* **deps**: update actions/add-to-project digest to 11ef9e1 ([#2539](https://github.com/camunda/tasklist/issues/2539))
* **deps**: update dependency @types/node to v18.14.0 ([#2521](https://github.com/camunda/tasklist/issues/2521))
* Refactor polling + infinite scrolling ([#2522](https://github.com/camunda/tasklist/issues/2522))
* Replace set-output ([#2536](https://github.com/camunda/tasklist/issues/2536))
* Improve lodash code splitting ([#2535](https://github.com/camunda/tasklist/issues/2535))
* Update Browserlist DB
* **deps**: update actions/add-to-project digest to 5b15b1a ([#2515](https://github.com/camunda/tasklist/issues/2515))
* **deps**: update actions/add-to-project digest to 8434539 ([#2513](https://github.com/camunda/tasklist/issues/2513))
* **deps**: update actions/add-to-project digest to ba4c19d ([#2504](https://github.com/camunda/tasklist/issues/2504))
* Add tracking to process start ([#2498](https://github.com/camunda/tasklist/issues/2498))
* Update Browserlist DB
* Cache Playwright install on GHA ([#2491](https://github.com/camunda/tasklist/issues/2491))
* Update visual regression setup ([#2487](https://github.com/camunda/tasklist/issues/2487))
* **deps**: update all non-major dependencies ([#2463](https://github.com/camunda/tasklist/issues/2463))
* update CHANGELOG.md
# v8.2.0-alpha4
## 🚀 New Features
* Replace feature flag with conditional rendering of processes menu item ([#2485](https://github.com/camunda/tasklist/issues/2485))
* **feature-flagged**: Implement process tile logic ([#2482](https://github.com/camunda/tasklist/issues/2482))
* **feature-flagged**: Implement first time modal for processes page ([#2481](https://github.com/camunda/tasklist/issues/2481))
* **feature-flagged**: Implement processes fetching ([#2479](https://github.com/camunda/tasklist/issues/2479))
* adding a new mock process with 2 user tasks ([#2477](https://github.com/camunda/tasklist/issues/2477))
* Style searchable select ([#2466](https://github.com/camunda/tasklist/issues/2466))
* not returning nulls for processDefinitionId ([#2467](https://github.com/camunda/tasklist/issues/2467))
* configure max-age for 2 years for HSTS header ([#2464](https://github.com/camunda/tasklist/issues/2464))
* adding feature to start process from tasklist apis ([#2450](https://github.com/camunda/tasklist/issues/2450))
* content-security-policy ([#2432](https://github.com/camunda/tasklist/issues/2432))
* adding processinstanceid and processdefinitionid filters ([#2435](https://github.com/camunda/tasklist/issues/2435))
* Update variable value field error message ([#2423](https://github.com/camunda/tasklist/issues/2423))
* **qa**: nightly Jenkins job to test backup and restore of data ([#2392](https://github.com/camunda/tasklist/issues/2392))
* adding a new form to test-data-generator ([#2398](https://github.com/camunda/tasklist/issues/2398))

## 💊 Bugfixes
* session not expiring ([#2459](https://github.com/camunda/tasklist/issues/2459))
* adding write permission to startProcess ([#2489](https://github.com/camunda/tasklist/issues/2489))
* **deps**: update dependency @carbon/elements to v11.19.0 ([#2469](https://github.com/camunda/tasklist/issues/2469))
* **deps**: update dependency @carbon/react to v1.22.0 ([#2470](https://github.com/camunda/tasklist/issues/2470))
* **deps**: update all non-major dependencies ([#2391](https://github.com/camunda/tasklist/issues/2391))
* **deps**: update dependency sass to v1.58.0 ([#2457](https://github.com/camunda/tasklist/issues/2457))
* **deps**: update dependency react-router-dom to v6.8.0 ([#2438](https://github.com/camunda/tasklist/issues/2438))
* **deps**: update dependency react-router-dom to v6.7.0 ([#2409](https://github.com/camunda/tasklist/issues/2409))
* **deps**: update dependency @carbon/elements to v11.18.0 ([#2415](https://github.com/camunda/tasklist/issues/2415))
* **deps**: update dependency @carbon/react to v1.21.0 ([#2416](https://github.com/camunda/tasklist/issues/2416))
* Fix missing form-js customization issues ([#2403](https://github.com/camunda/tasklist/issues/2403))
* **deps**: update dependency final-form-arrays to v3.1.0 ([#2394](https://github.com/camunda/tasklist/issues/2394))
* **deps**: update all non-major dependencies ([#2385](https://github.com/camunda/tasklist/issues/2385))
* **deps**: update dependency @carbon/elements to v11.17.0 ([#2379](https://github.com/camunda/tasklist/issues/2379))
* **deps**: update dependency @carbon/react to v1.20.0 ([#2380](https://github.com/camunda/tasklist/issues/2380))
* **chore**: use mvn for start app ([#2370](https://github.com/camunda/tasklist/issues/2370))

## 🧹 Chore
* **backend**: update Zeebe and Identity to 8.2.0-alpha4 ([#2490](https://github.com/camunda/tasklist/issues/2490))
* **deps**: update dependency @types/node to v18.13.0 ([#2475](https://github.com/camunda/tasklist/issues/2475))
* **deps**: update dependency monaco-editor to v0.35.0 ([#2480](https://github.com/camunda/tasklist/issues/2480))
* **deps**: update actions/add-to-project digest to 28a69b2 ([#2486](https://github.com/camunda/tasklist/issues/2486))
* **deps**: update actions/add-to-project digest to 5a55c0c ([#2473](https://github.com/camunda/tasklist/issues/2473))
* Enable tracking on dev
* Revert header menu order
* bump json5 from 1.0.1 to 1.0.2 in /client ([#2471](https://github.com/camunda/tasklist/issues/2471))
* bump http-cache-semantics from 4.1.0 to 4.1.1 in /client ([#2468](https://github.com/camunda/tasklist/issues/2468))
* bump luxon from 3.2.0 to 3.2.1 in /client ([#2384](https://github.com/camunda/tasklist/issues/2384))
* bump express from 4.17.1 to 4.18.2 in /client ([#2322](https://github.com/camunda/tasklist/issues/2322))
* bump qs from 6.5.2 to 6.5.3 in /client ([#2321](https://github.com/camunda/tasklist/issues/2321))
* bump decode-uri-component from 0.2.0 to 0.2.2 in /client ([#2305](https://github.com/camunda/tasklist/issues/2305))
* Update Browserlist DB
* Add mock setup ([#2456](https://github.com/camunda/tasklist/issues/2456))
* make mixpanel available as global variable
* **deps**: update actions/add-to-project digest to 09abe09 ([#2455](https://github.com/camunda/tasklist/issues/2455))
* **deps**: update dependency testcafe to v2.3.0 ([#2447](https://github.com/camunda/tasklist/issues/2447))
* Mock requests in visual regression tests and add test for empty page ([#2441](https://github.com/camunda/tasklist/issues/2441))
* **GHA**: use CI Nexus as co-located pull-through cache for Maven artifacts via ~/.m2/settings.xml ([#2448](https://github.com/camunda/tasklist/issues/2448))
* **feature-flagged**: Add new processes tab ([#2449](https://github.com/camunda/tasklist/issues/2449))
* Update Browserlist DB
* **deps**: update hashicorp/vault-action digest to 130d1f5 ([#2436](https://github.com/camunda/tasklist/issues/2436))
* **deps**: update hashicorp/vault-action action to v2.5.0 ([#2437](https://github.com/camunda/tasklist/issues/2437))
* Add add attributes for complete task tracking event ([#2424](https://github.com/camunda/tasklist/issues/2424))
* **deps**: update hashicorp/vault-action digest to d34ee14 ([#2433](https://github.com/camunda/tasklist/issues/2433))
* **deps**: update actions/add-to-project digest to add81c3 ([#2431](https://github.com/camunda/tasklist/issues/2431))
* **deps**: update dependency @types/jest to v29.4.0 ([#2428](https://github.com/camunda/tasklist/issues/2428))
* **deps**: update hashicorp/vault-action digest to 77bab83 ([#2425](https://github.com/camunda/tasklist/issues/2425))
* **deps**: update dependency @playwright/test to v1.30.0 ([#2426](https://github.com/camunda/tasklist/issues/2426))
* **deps**: update dependency msw to v1 ([#2427](https://github.com/camunda/tasklist/issues/2427))
* **deps**: update hashicorp/vault-action digest to 7318a98 ([#2420](https://github.com/camunda/tasklist/issues/2420))
* **deps**: update actions/add-to-project digest to bcf48a5 ([#2421](https://github.com/camunda/tasklist/issues/2421))
* bump netty-bom from 4.1.86.Final to 4.1.87.Final ([#2399](https://github.com/camunda/tasklist/issues/2399))
* bump assertj-core from 3.23.1 to 3.24.2 ([#2400](https://github.com/camunda/tasklist/issues/2400))
* bump maven-checkstyle-plugin from 3.2.0 to 3.2.1 ([#2401](https://github.com/camunda/tasklist/issues/2401))
* Add visual regression tests ([#2404](https://github.com/camunda/tasklist/issues/2404))
* Update Browserlist DB
* **deps**: update hashicorp/vault-action digest to b08bc49 ([#2405](https://github.com/camunda/tasklist/issues/2405))
* bump version.micrometer from 1.10.2 to 1.10.3 ([#2386](https://github.com/camunda/tasklist/issues/2386))
* bump byte-buddy from 1.12.20 to 1.12.22 ([#2393](https://github.com/camunda/tasklist/issues/2393))
* bump maven-surefire-plugin from 3.0.0-M7 to 3.0.0-M8 ([#2388](https://github.com/camunda/tasklist/issues/2388))
* bump maven-failsafe-plugin from 3.0.0-M7 to 3.0.0-M8 ([#2389](https://github.com/camunda/tasklist/issues/2389))
* bump mvc-auth-commons from 1.9.3 to 1.9.4 ([#2390](https://github.com/camunda/tasklist/issues/2390))
* bump mockito-core from 4.10.0 to 5.0.0 ([#2396](https://github.com/camunda/tasklist/issues/2396))
* Update Browserlist DB
* **deps**: update all non-major dependencies ([#2381](https://github.com/camunda/tasklist/issues/2381))
* Update Browserlist DB
* update CHANGELOG.md
# v8.2.0-alpha3
## 🚀 New Features
* **backend**: return 502 in case of Elastic connection error ([#2365](https://github.com/camunda/tasklist/issues/2365))
* **backend**: endpoint to list backups ([#2364](https://github.com/camunda/tasklist/issues/2364))
* Migrate Tasklist to Carbon design ([#2347](https://github.com/camunda/tasklist/issues/2347))
* **backend**: Get backup state endpoint ([#2362](https://github.com/camunda/tasklist/issues/2362))
* **backend**: delete backup endpoint ([#2335](https://github.com/camunda/tasklist/issues/2335))

## 💊 Bugfixes
* Fix adornements styles
* **backend**: rename `backup` endpoint to `backups` ([#2372](https://github.com/camunda/tasklist/issues/2372))
* **deps**: update dependency react-router-dom to v6.6.1 ([#2354](https://github.com/camunda/tasklist/issues/2354))
* **deps**: update dependency sass to v1.57.1 ([#2355](https://github.com/camunda/tasklist/issues/2355))
* **backend**: use domain instead of backendDomain ([#2345](https://github.com/camunda/tasklist/issues/2345))
* **deps**: update dependency @carbon/elements to v11.16.0 ([#2332](https://github.com/camunda/tasklist/issues/2332))
* **deps**: update dependency @carbon/react to v1.19.0 ([#2333](https://github.com/camunda/tasklist/issues/2333))
* Show footer on task details ([#2328](https://github.com/camunda/tasklist/issues/2328))

## 🧹 Chore
* **backend**: expose `backups` actuator endpoint
* **backend**: update Zeebe abd identity to 8.2.0-alpha3 ([#2373](https://github.com/camunda/tasklist/issues/2373))
* **deps**: update node.js to v16.19.0 ([#2343](https://github.com/camunda/tasklist/issues/2343))
* **deps**: update all non-major dependencies ([#2334](https://github.com/camunda/tasklist/issues/2334))
* **deps**: update dependency eslint-config-prettier to v8.6.0 ([#2367](https://github.com/camunda/tasklist/issues/2367))
* **deps**: update dependency testcafe to v2.2.0 ([#2368](https://github.com/camunda/tasklist/issues/2368))
* Skip flaky test
* Remove logging
* **deps**: update actions/add-to-project digest to aebf7de ([#2353](https://github.com/camunda/tasklist/issues/2353))
* Update Browserlist DB
* bump version.elasticsearch from 7.17.7 to 7.17.8 ([#2351](https://github.com/camunda/tasklist/issues/2351))
* bump mockito-core from 4.9.0 to 4.10.0 ([#2349](https://github.com/camunda/tasklist/issues/2349))
* bump netty-bom from 4.1.85.Final to 4.1.86.Final ([#2341](https://github.com/camunda/tasklist/issues/2341))
* bump byte-buddy from 1.12.19 to 1.12.20 ([#2350](https://github.com/camunda/tasklist/issues/2350))
* **project**: use docker 20 dind ([#2339](https://github.com/camunda/tasklist/issues/2339))
* **deps**: update actions/add-to-project digest to dc0c796 ([#2346](https://github.com/camunda/tasklist/issues/2346))
* **preview-env**: Add missing requests and limits resources ([#2336](https://github.com/camunda/tasklist/issues/2336))
* **deps**: update all non-major dependencies ([#2310](https://github.com/camunda/tasklist/issues/2310))
* Update Browserlist DB
* **deps**: update actions/add-to-project digest to 2558057 ([#2331](https://github.com/camunda/tasklist/issues/2331))
* **deps**: update actions/add-to-project digest to f8f1995 ([#2329](https://github.com/camunda/tasklist/issues/2329))
# v8.2.0-alpha2
## 🚀 New Features
* Implement App switcher ([#2319](https://github.com/camunda/tasklist/issues/2319))
* **backend**: add c8Links to User object ([#2309](https://github.com/camunda/tasklist/issues/2309))
* **backend**: migrate assignee ([#2291](https://github.com/camunda/tasklist/issues/2291))
* **backend**: add optional parameter `allowOverrideAssignment` ([#2247](https://github.com/camunda/tasklist/issues/2247))

## 💊 Bugfixes
* **deps**: update dependency @carbon/elements to v11.15.0 ([#2302](https://github.com/camunda/tasklist/issues/2302))
* **deps**: update dependency @carbon/react to v1.18.0 ([#2303](https://github.com/camunda/tasklist/issues/2303))
* Fix Mixpanel loading ([#2307](https://github.com/camunda/tasklist/issues/2307))
* **deps**: update dependency mobx to v6.7.0 ([#2255](https://github.com/camunda/tasklist/issues/2255))
* **deps**: update dependency @carbon/elements to v11.14.0 ([#2249](https://github.com/camunda/tasklist/issues/2249))
* **deps**: update dependency react-textarea-autosize to v8.4.0 ([#2246](https://github.com/camunda/tasklist/issues/2246))
* **deps**: update dependency @carbon/react to v1.17.0 ([#2250](https://github.com/camunda/tasklist/issues/2250))
* **deps**: update dependency @carbon/elements to v11.13.0 ([#2215](https://github.com/camunda/tasklist/issues/2215))
* **deps**: update dependency @carbon/react to v1.16.0 ([#2216](https://github.com/camunda/tasklist/issues/2216))
* **deps**: update dependency sass to v1.56.0 ([#2229](https://github.com/camunda/tasklist/issues/2229))

## 🧹 Chore
* **deps**: update actions/add-to-project digest to 31901d2 ([#2327](https://github.com/camunda/tasklist/issues/2327))
* Bump @camunda/camunda-composite-components
* **project**: update Zeebe and Identity versions to 8.2.0-alpha2 ([#2324](https://github.com/camunda/tasklist/issues/2324))
* **deps**: update actions/add-to-project digest to 06e54d7 ([#2320](https://github.com/camunda/tasklist/issues/2320))
* **deps**: update all non-major dependencies ([#2283](https://github.com/camunda/tasklist/issues/2283))
* **deps**: update dependency lint-staged to v13.1.0 ([#2304](https://github.com/camunda/tasklist/issues/2304))
* Update Browserlist DB
* **deps**: update actions/add-to-project digest to a4a63c3 ([#2297](https://github.com/camunda/tasklist/issues/2297))
* **pom**: update Spring Boot 2.6.14 ([#2295](https://github.com/camunda/tasklist/issues/2295))
* **preview-environments**: Use proper secret from tasklist namespace ([#2290](https://github.com/camunda/tasklist/issues/2290))
* **deps**: update actions/add-to-project digest to 7540d63 ([#2293](https://github.com/camunda/tasklist/issues/2293))
* **deps**: update actions/add-to-project digest to 1885da2 ([#2292](https://github.com/camunda/tasklist/issues/2292))
* bump version.micrometer from 1.10.1 to 1.10.2 ([#2278](https://github.com/camunda/tasklist/issues/2278))
* **preview-environments**: Update preview-environments to use proper cert. ([#2272](https://github.com/camunda/tasklist/issues/2272))
* bump docker-java-core from 3.2.13 to 3.2.14 ([#2275](https://github.com/camunda/tasklist/issues/2275))
* Remove unnecessary lockfile
* **deps**: update actions/add-to-project digest to 29766ca ([#2277](https://github.com/camunda/tasklist/issues/2277))
* **deps**: update dependency msw to v0.49.0 ([#2265](https://github.com/camunda/tasklist/issues/2265))
* **deps**: update all non-major dependencies ([#2254](https://github.com/camunda/tasklist/issues/2254))
* **deps**: update dependency prettier to v2.8.0 ([#2274](https://github.com/camunda/tasklist/issues/2274))
* bump version.micrometer from 1.10.0 to 1.10.1 ([#2259](https://github.com/camunda/tasklist/issues/2259))
* bump byte-buddy from 1.12.18 to 1.12.19 ([#2263](https://github.com/camunda/tasklist/issues/2263))
* bump version.jackson from 2.14.0 to 2.14.1 ([#2271](https://github.com/camunda/tasklist/issues/2271))
* **deps**: update actions/add-to-project digest to d3e23d3 ([#2273](https://github.com/camunda/tasklist/issues/2273))
* **deps**: update actions/add-to-project digest to b6a7221 ([#2270](https://github.com/camunda/tasklist/issues/2270))
* **deps**: update dependency typescript to v4.9.3 ([#2258](https://github.com/camunda/tasklist/issues/2258))
* **preview-environments**: Create new preview-environments with proper prefix; allow destruction of preview-environments with both old and new naming schema. ([#2266](https://github.com/camunda/tasklist/issues/2266))
* Update Browserlist DB
* bump mockito-core from 4.8.1 to 4.9.0 ([#2257](https://github.com/camunda/tasklist/issues/2257))
* bump elasticsearch from 1.17.5 to 1.17.6 ([#2261](https://github.com/camunda/tasklist/issues/2261))
* **deps**: update all non-major dependencies ([#2251](https://github.com/camunda/tasklist/issues/2251))
* **deps**: update dependency testcafe to v2.1.0 ([#2248](https://github.com/camunda/tasklist/issues/2248))
* bump mvc-auth-commons from 1.9.2 to 1.9.3 ([#2212](https://github.com/camunda/tasklist/issues/2212))
* bump maven-shade-plugin from 3.4.0 to 3.4.1 ([#2213](https://github.com/camunda/tasklist/issues/2213))
* bump version.jackson from 2.13.4 to 2.14.0 ([#2239](https://github.com/camunda/tasklist/issues/2239))
* bump version.micrometer from 1.9.5 to 1.10.0 ([#2240](https://github.com/camunda/tasklist/issues/2240))
* bump netty-bom from 4.1.84.Final to 4.1.85.Final ([#2245](https://github.com/camunda/tasklist/issues/2245))
* **deps**: update all non-major dependencies ([#2203](https://github.com/camunda/tasklist/issues/2203))
* **deps**: update dependency msw to v0.48.0 ([#2241](https://github.com/camunda/tasklist/issues/2241))
* **deps**: update hashicorp/vault-action digest to 8fa61e9 ([#2242](https://github.com/camunda/tasklist/issues/2242))
* **deps**: update helm release camunda-platform to v8.1.1 ([#2244](https://github.com/camunda/tasklist/issues/2244))
* **preview-env**: Track camunda helm chart version ([#2243](https://github.com/camunda/tasklist/issues/2243))
* **backend**: test docker image to be run with arbitrary user ([#2186](https://github.com/camunda/tasklist/issues/2186))
* **deps**: update actions/add-to-project digest to 960fbad ([#2228](https://github.com/camunda/tasklist/issues/2228))
* bump mockito-core from 4.8.0 to 4.8.1 ([#2188](https://github.com/camunda/tasklist/issues/2188))
* bump version.elasticsearch from 7.17.6 to 7.17.7 ([#2196](https://github.com/camunda/tasklist/issues/2196))
* bump jib-maven-plugin from 3.3.0 to 3.3.1 ([#2211](https://github.com/camunda/tasklist/issues/2211))
* Update Browserlist DB
# v8.2.0-alpha1
## 💊 Bugfixes
* **backend**: Use email, username as assignee ([#2158](https://github.com/camunda/tasklist/issues/2158))
* **deps**: update all non-major dependencies ([#2173](https://github.com/camunda/tasklist/issues/2173))
* **deps**: update dependency @carbon/react to v1.15.0 ([#2166](https://github.com/camunda/tasklist/issues/2166))
* **deps**: update dependency @carbon/elements to v11.12.0 ([#2165](https://github.com/camunda/tasklist/issues/2165))
* **deps**: update dependency @bpmn-io/form-js to v0.9.7 ([#2152](https://github.com/camunda/tasklist/issues/2152))
* **deps**: update dependency sass to v1.55.0 ([#2145](https://github.com/camunda/tasklist/issues/2145))
* **deps**: update dependency @carbon/elements to v11.11.0 ([#2144](https://github.com/camunda/tasklist/issues/2144))
* **deps**: update dependency react-router-dom to v6.4.2 ([#2087](https://github.com/camunda/tasklist/issues/2087))
* **deps**: update dependency @bpmn-io/form-js to v0.9.6 ([#2143](https://github.com/camunda/tasklist/issues/2143))
* **deps**: update all non-major dependencies ([#2137](https://github.com/camunda/tasklist/issues/2137))
* **deps**: update dependency @carbon/react to v1.14.0 ([#2091](https://github.com/camunda/tasklist/issues/2091))
* **preview-env**: Add missing input for teardown workflow
* **preview-env**: Add missing env var ([#2139](https://github.com/camunda/tasklist/issues/2139))
* Revert dep update

## 🧹 Chore
* **backend**: update Zeebe abd Identity to 8.2.0-alpha1 ([#2225](https://github.com/camunda/tasklist/issues/2225))
* **pom**: update Spring Boot to 2.6.13 ([#2223](https://github.com/camunda/tasklist/issues/2223))
* **Dockerfile**: update Docker base image to be temurin 17 ([#2208](https://github.com/camunda/tasklist/issues/2208))
* Update Browserlist DB
* **preview-env**: Escape special characters from branch names ([#2209](https://github.com/camunda/tasklist/issues/2209))
* **deps**: update actions/add-to-project digest to c7ca843 ([#2205](https://github.com/camunda/tasklist/issues/2205))
* **preview-env**: Cleanup old preview env actions ([#2198](https://github.com/camunda/tasklist/issues/2198))
* **preview-env**: test the new global preview env teardown action ([#2199](https://github.com/camunda/tasklist/issues/2199))
* **deps**: update hashicorp/vault-action digest to 132f1c6 ([#2201](https://github.com/camunda/tasklist/issues/2201))
* **deps**: update actions/add-to-project digest to 9eaa856 ([#2200](https://github.com/camunda/tasklist/issues/2200))
* **seed**: rename notification skip trait to comply with new plugin version ([#2177](https://github.com/camunda/tasklist/issues/2177))
* **deps**: update actions/add-to-project digest to 8f9378c ([#2197](https://github.com/camunda/tasklist/issues/2197))
* **preview-env**: test the new global preview env action ([#2168](https://github.com/camunda/tasklist/issues/2168))
* **deps**: update dependency @types/node to v18 ([#2193](https://github.com/camunda/tasklist/issues/2193))
* **deps**: update hashicorp/vault-action digest to 32d00a1 ([#2192](https://github.com/camunda/tasklist/issues/2192))
* **deps**: update actions/add-to-project digest to 73dbef5 ([#2190](https://github.com/camunda/tasklist/issues/2190))
* **deps**: update dependency @testing-library/testcafe to v5 ([#2185](https://github.com/camunda/tasklist/issues/2185))
* **deps**: update dependency @types/node to v16.18.0 ([#2187](https://github.com/camunda/tasklist/issues/2187))
* Update Browserlist DB
* **deps**: update actions/add-to-project digest to 7e0e2c5 ([#2179](https://github.com/camunda/tasklist/issues/2179))
* Remove Operate team issue to board automation
* **deps**: update actions/add-to-project digest to 394bc02 ([#2172](https://github.com/camunda/tasklist/issues/2172))
* **deps**: update all non-major dependencies ([#2167](https://github.com/camunda/tasklist/issues/2167))
* **deps**: update dependency @types/jest to v29.2.0 ([#2170](https://github.com/camunda/tasklist/issues/2170))
* add add_to_hto_project action ([#2169](https://github.com/camunda/tasklist/issues/2169))
* Update Browserlist DB
* **deps**: update hashicorp/vault-action digest to 32838a0 ([#2163](https://github.com/camunda/tasklist/issues/2163))
* **deps**: update all non-major dependencies ([#2160](https://github.com/camunda/tasklist/issues/2160))
* bump zeebe-test-container from 3.5.0 to 3.5.2 ([#2116](https://github.com/camunda/tasklist/issues/2116))
* bump netty-bom from 4.1.82.Final to 4.1.84.Final ([#2156](https://github.com/camunda/tasklist/issues/2156))
* **deps**: update node.js to v16.18.0 ([#2157](https://github.com/camunda/tasklist/issues/2157))
* bump elasticsearch from 1.17.3 to 1.17.5 ([#2129](https://github.com/camunda/tasklist/issues/2129))
* bump byte-buddy from 1.12.17 to 1.12.18 ([#2150](https://github.com/camunda/tasklist/issues/2150))
* bump version.micrometer from 1.9.4 to 1.9.5 ([#2151](https://github.com/camunda/tasklist/issues/2151))
* **deps**: update amannn/action-semantic-pull-request action to v5 ([#2155](https://github.com/camunda/tasklist/issues/2155))
* **backend**: adjust importer modules add 8.2 remove 8.0  ([#2141](https://github.com/camunda/tasklist/issues/2141))
* **deps**: update dependency @types/node to v16.11.65 ([#2149](https://github.com/camunda/tasklist/issues/2149))
* **deps**: update dependency @types/jest to v29 ([#2148](https://github.com/camunda/tasklist/issues/2148))
* Update Zeebe for E2E tests
* Fix deploy action ([#2146](https://github.com/camunda/tasklist/issues/2146))
* **deps**: update bobheadxi/deployments digest to 9d4477f ([#2006](https://github.com/camunda/tasklist/issues/2006))
* **deps**: update dependency zeebe-node to v8.1.2 ([#2142](https://github.com/camunda/tasklist/issues/2142))
* **deps**: update dependency testcafe to v2 ([#2092](https://github.com/camunda/tasklist/issues/2092))
* Update Browserlist DB
* **preview-env**: Split the deploy-preview actions into create and destroy actions ([#2134](https://github.com/camunda/tasklist/issues/2134))
* Fix version on package.json
* **deps**: update definitelytyped ([#2049](https://github.com/camunda/tasklist/issues/2049))
# v8.1.0
## 🚀 New Features
* **backend**: Get backup state endpoint ([#2117](https://github.com/camunda/tasklist/issues/2117))
* **backend**: Create Backup endpoint ([#2112](https://github.com/camunda/tasklist/issues/2112))
* run Zeebe Importer multi-threaded ([#2089](https://github.com/camunda/tasklist/issues/2089))
* branch-deploy label for preview envs ([#2079](https://github.com/camunda/tasklist/issues/2079))
* **metrics**: measure time to import Zeebe records ([#2073](https://github.com/camunda/tasklist/issues/2073))

## 💊 Bugfixes
* **chore**: add Spring JWT settings for API access via Identity ([#2094](https://github.com/camunda/tasklist/issues/2094))
* gh deployment ref was wrong for PRs ([#2100](https://github.com/camunda/tasklist/issues/2100))
* **deps**: update dependency @carbon/elements to v11.10.0 ([#2090](https://github.com/camunda/tasklist/issues/2090))
* release the RecordsReader thread when queue has no capacity ([#2075](https://github.com/camunda/tasklist/issues/2075))
* **test**: execute start timer only once ([#2084](https://github.com/camunda/tasklist/issues/2084))
* **deps**: update dependency @carbon/react to v1.12.0 ([#2067](https://github.com/camunda/tasklist/issues/2067))
* **test**: wait for task cancellation before doing the assertions ([#2081](https://github.com/camunda/tasklist/issues/2081))
* fix quotation for cat command in the changelog workflow ([#2068](https://github.com/camunda/tasklist/issues/2068))
* **deps**: update all non-major dependencies ([#2040](https://github.com/camunda/tasklist/issues/2040))

## 🧹 Chore
* Revert wrong CHANGELOG
* Use action to avoid errors with unescaped characters ([#2131](https://github.com/camunda/tasklist/issues/2131))
* update CHANGELOG.md
* **project**: upgrade-identity-to-8.1.0 ([#2128](https://github.com/camunda/tasklist/issues/2128))
* **project**: upgrade-zeebe-to-8.1.0 ([#2118](https://github.com/camunda/tasklist/issues/2118))
* Update Browserlist DB
* **backend**: update Zeebe and Identity till 8.1.0-alpha5` ([#2107](https://github.com/camunda/tasklist/issues/2107))
* Update Browserlist DB
* bump maven-jar-plugin from 3.2.2 to 3.3.0 ([#2085](https://github.com/camunda/tasklist/issues/2085))
* bump version.log4j from 2.18.0 to 2.19.0 ([#2093](https://github.com/camunda/tasklist/issues/2093))
* bump byte-buddy from 1.12.16 to 1.12.17 ([#2098](https://github.com/camunda/tasklist/issues/2098))
* update processed positions periodically ([#2078](https://github.com/camunda/tasklist/issues/2078))
* Update Browserlist DB
* **deps**: update all non-major dependencies ([#2086](https://github.com/camunda/tasklist/issues/2086))
* **deps**: update dependency typescript to v4.8.3 ([#2045](https://github.com/camunda/tasklist/issues/2045))
* **deps**: update dependency msw to v0.47.3 ([#2051](https://github.com/camunda/tasklist/issues/2051))
* bump maven-shade-plugin from 3.3.0 to 3.4.0 ([#2083](https://github.com/camunda/tasklist/issues/2083))
* Add Carbon dependencies to master so they're updated
* bump mockito-core from 4.7.0 to 4.8.0 ([#2064](https://github.com/camunda/tasklist/issues/2064))
* bump netty-bom from 4.1.80.Final to 4.1.82.Final ([#2076](https://github.com/camunda/tasklist/issues/2076))
* bump jib-maven-plugin from 3.2.1 to 3.3.0 ([#2052](https://github.com/camunda/tasklist/issues/2052))
* bump byte-buddy from 1.12.14 to 1.12.16 ([#2070](https://github.com/camunda/tasklist/issues/2070))
* bump version.jackson from 2.13.3 to 2.13.4 ([#2054](https://github.com/camunda/tasklist/issues/2054))
* bump version.micrometer from 1.9.3 to 1.9.4 ([#2071](https://github.com/camunda/tasklist/issues/2071))
* Update Browserlist DB
* update CHANGELOG.md
# v8.1.0-alpha5

## 🚀 New Features

- Support dynamic forms on Tasklist ([#2053](https://github.com/camunda/tasklist/issues/2053))
- Use Monaco editor on Tasklist ([#1963](https://github.com/camunda/tasklist/issues/1963))

## 💊 Bugfixes

- **deps**: update dependency @carbon/react to v1.11.0 ([#2036](https://github.com/camunda/tasklist/issues/2036))
- **deps**: update dependency date-fns to v2.29.2 ([#2032](https://github.com/camunda/tasklist/issues/2032))
- **deps**: update dependency graphql to v16.6.0 ([#2031](https://github.com/camunda/tasklist/issues/2031))
- use single quote to prevent revert commits breaking the changelog ([#2023](https://github.com/camunda/tasklist/issues/2023))
- **deps**: update dependency @carbon/react to v1.9.0 ([#1986](https://github.com/camunda/tasklist/issues/1986))
- **deps**: update dependency sass to v1.54.3 ([#1983](https://github.com/camunda/tasklist/issues/1983))
- **deps**: update dependency mobx to v6.6.1 ([#1982](https://github.com/camunda/tasklist/issues/1982))
- **deps**: update dependency @carbon/react to v1.8.0 ([#1977](https://github.com/camunda/tasklist/issues/1977))
- **deps**: update dependency date-fns to v2.29.1 ([#1980](https://github.com/camunda/tasklist/issues/1980))
- **deps**: update all non-major dependencies ([#1897](https://github.com/camunda/tasklist/issues/1897))

## 🧹 Chore

- **backend**: update Zeebe and Identity till 8.1.0-alpha5 ([#2063](https://github.com/camunda/tasklist/issues/2063))
- Update Browserlist DB
- Create workflow for updating Browserlist DB
- bump netty-bom from 4.1.79.Final to 4.1.80.Final ([#2048](https://github.com/camunda/tasklist/issues/2048))
- bump byte-buddy from 1.12.13 to 1.12.14 ([#2039](https://github.com/camunda/tasklist/issues/2039))
- bump maven-checkstyle-plugin from 3.1.2 to 3.2.0 ([#2041](https://github.com/camunda/tasklist/issues/2041))
- bump version.elasticsearch from 7.17.5 to 7.17.6 ([#2043](https://github.com/camunda/tasklist/issues/2043))
- **ci**: update Zeebe version in preview envs ([#2044](https://github.com/camunda/tasklist/issues/2044))
- **deps**: update dependency msw to v0.45.0 ([#2037](https://github.com/camunda/tasklist/issues/2037))
- **deps**: update all non-major dependencies ([#2035](https://github.com/camunda/tasklist/issues/2035))
- **ci**: recreate argo app on apply ([#2038](https://github.com/camunda/tasklist/issues/2038))
- **preview-env**: enable persistency for zeebe and ES ([#2034](https://github.com/camunda/tasklist/issues/2034))
- **deps**: update node.js to v16.17.0 ([#2030](https://github.com/camunda/tasklist/issues/2030))
- **deps**: update all non-major dependencies ([#2028](https://github.com/camunda/tasklist/issues/2028))
- **deps**: update hashicorp/vault-action digest to 7d98524 ([#2027](https://github.com/camunda/tasklist/issues/2027))
- bump mockito-core from 4.6.1 to 4.7.0 ([#2025](https://github.com/camunda/tasklist/issues/2025))
- bump maven-javadoc-plugin from 3.4.0 to 3.4.1 ([#2024](https://github.com/camunda/tasklist/issues/2024))
- bump version.micrometer from 1.9.2 to 1.9.3 ([#1998](https://github.com/camunda/tasklist/issues/1998))
- **deps**: update dependency lint-staged to v13 ([#2019](https://github.com/camunda/tasklist/issues/2019))
- **deps**: update all non-major dependencies ([#2008](https://github.com/camunda/tasklist/issues/2008))
- **deps**: update dlavrenuek/conventional-changelog-action action to v1.2.1 ([#2011](https://github.com/camunda/tasklist/issues/2011))
- **deps**: update actions/checkout action to v3 ([#2014](https://github.com/camunda/tasklist/issues/2014))
- **deps**: update dependency docker.elastic.co/elasticsearch/elasticsearch to v7.17.5 ([#2010](https://github.com/camunda/tasklist/issues/2010))
- **deps**: update actions/setup-node action to v3 ([#2016](https://github.com/camunda/tasklist/issues/2016))
- **deps**: update actions/cache action to v3 ([#2013](https://github.com/camunda/tasklist/issues/2013))
- **deps**: update hashicorp/vault-action action to v2.4.1 ([#2012](https://github.com/camunda/tasklist/issues/2012))
- **deps**: update actions/setup-java action to v3 ([#2015](https://github.com/camunda/tasklist/issues/2015))
- **deps**: update actions/add-to-project action to v0.3.0 ([#2009](https://github.com/camunda/tasklist/issues/2009))
- **deps**: update hashicorp/vault-action digest to f380d92 ([#2007](https://github.com/camunda/tasklist/issues/2007))
- Enable Renovate on CI folders ([#2000](https://github.com/camunda/tasklist/issues/2000))
- bump eventsource from 1.1.0 to 1.1.2 in /client ([#1999](https://github.com/camunda/tasklist/issues/1999))
- bump protobufjs from 6.11.2 to 6.11.3 in /client ([#1882](https://github.com/camunda/tasklist/issues/1882))
- bump terser from 4.8.0 to 4.8.1 in /client ([#1943](https://github.com/camunda/tasklist/issues/1943))
- **deps**: update dependency msw to v0.44.2 ([#1995](https://github.com/camunda/tasklist/issues/1995))
- **deps**: update node.js to v16.16.0 ([#1996](https://github.com/camunda/tasklist/issues/1996))
- Revert "chore(deps): update dependency @types/jest to v28" ([#1997](https://github.com/camunda/tasklist/issues/1997))
- Skip backend tests on dependency updates PRs
- **docs**: document VariableInput ([#1990](https://github.com/camunda/tasklist/issues/1990))
- **deps**: update dependency @types/jest to v28 ([#1984](https://github.com/camunda/tasklist/issues/1984))
- **deps**: update all non-major dependencies ([#1979](https://github.com/camunda/tasklist/issues/1979))
- Update CHANGELOG.md for 8.1.0-alpha4
- **preview-environments**: refactor to include ingress annotations from shared Helm chart ([#1978](https://github.com/camunda/tasklist/issues/1978))
- **deps**: update dependency lint-staged to v12.5.0 ([#1899](https://github.com/camunda/tasklist/issues/1899))
- **deps**: update dependency prettier to v2.7.1 ([#1974](https://github.com/camunda/tasklist/issues/1974))
- **deps**: update dependency typescript to v4.7.4 ([#1975](https://github.com/camunda/tasklist/issues/1975))
- **deps**: update dependency eslint-plugin-prettier to v4.2.1 ([#1950](https://github.com/camunda/tasklist/issues/1950))
- **deps**: update dependency testcafe to v1.20.0 ([#1901](https://github.com/camunda/tasklist/issues/1901))
- update pull request template ([#1968](https://github.com/camunda/tasklist/issues/1968))
- move utilities to the commons package ([#1971](https://github.com/camunda/tasklist/issues/1971))

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
