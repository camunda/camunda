#!/bin/bash

for i in {1..300}; do
  curl 'http://localhost:3000/api/process-instances/2251799813685299/modify' \
    -H 'Accept: */*' \
    -H 'Accept-Language: en-US,en;q=0.9,pt;q=0.8' \
    -H 'Cache-Control: no-cache' \
    -H 'Connection: keep-alive' \
    -H 'Content-Type: application/json' \
    -b 'camunda-session=889336543DA437843D14A269A8FD4939; X-CSRF-TOKEN=5aef1dd6-52c7-4e99-857d-63d86e6a4ea3' \
    -H 'Origin: http://localhost:3000' \
    -H 'Pragma: no-cache' \
    -H 'Referer: http://localhost:3000/processes/2251799813685299' \
    -H 'Sec-Fetch-Dest: empty' \
    -H 'Sec-Fetch-Mode: cors' \
    -H 'Sec-Fetch-Site: same-origin' \
    -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36' \
    -H 'X-CSRF-TOKEN: GlwzFW1pVlGHVjAosaZZqCJVQCGuYuGlO9d_MRdGm6TyOdQmLz1Wc1wNMmeqYwJLhottzRtsbRmbVYWIDeQbCSEjrcXGXLUV' \
    -H 'sec-ch-ua: "Google Chrome";v="141", "Not?A_Brand";v="8", "Chromium";v="141"' \
    -H 'sec-ch-ua-mobile: ?0' \
    -H 'sec-ch-ua-platform: "Linux"' \
    --data-raw '{"modifications":[{"modification":"ADD_TOKEN","toFlowNodeId":"Activity_0awbj0c3"}]}'
  
  echo "Request $i completed"
done
