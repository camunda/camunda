#!/bin/bash -eux

chmod +x clients/go/cmd/zbctl/dist/zbctl

zbctl="clients/go/cmd/zbctl/dist/zbctl"

"${zbctl}" create instance e2e_testbench_protocol --variables "{\"zeebeImage\":\"$ZEEBE_IMAGE\", \"generationTemplate\":\"$GENERATION_TEMPLATE\", \"channel\":\"Internal Dev\", \"clusterPlan\":\"Production - M\", \"region\":\"new chaos\", \"properties\":[\"allInstancesAreCompleted\"], \"testProcessId\": \"e2e-test\", \"branch\": \"$BRANCH_NAME\", \"build\": \"$BUILD_URL\", \"testParams\": { \"maxTestDuration\": \"$MAX_TEST_DURATION\", \"starter\": [ {\"rate\": 50, \"processId\": \"one-task-one-timer\" } ], \"verifier\" : { \"maxInstanceDuration\" : \"15m\" } } }"
