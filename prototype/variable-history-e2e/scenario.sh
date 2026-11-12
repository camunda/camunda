#!/bin/bash
S="$(cd "$(dirname "$0")" && pwd)"; B=localhost:8190; J='Content-Type: application/json'
tok(){ curl -s -X POST localhost:8099/default/token -d "grant_type=client_credentials&client_id=$1&client_secret=x" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])"; }
api(){ local who=$1; shift; curl -s -H "Authorization: Bearer $(tok $who)" "$@"; }
setvar(){ api $1 -o /dev/null -X PUT $B/v2/element-instances/$PI/variables -H "$J" -d "$2"; sleep 1.1; }
api demo -F "resources=@$S/vh.bpmn" $B/v2/deployments -o /dev/null
api demo -F "resources=@$S/bulk.bpmn" $B/v2/deployments -o /dev/null
cust(){ echo "{\"variables\":{\"customer\":{\"id\":\"c-7700\",\"name\":\"$2\",\"email\":\"$3\",\"tier\":\"$4\",\"address\":{\"street\":\"$5\",\"city\":\"$6\",\"country\":\"BE\"}}}}"; }
PI=$(api shop-app -X POST $B/v2/process-instances -H "$J" -d "{\"processDefinitionId\":\"order-fulfillment\",\"variables\":{\"orderId\":\"7700\",\"chargeAmount\":100,\"riskScore\":100,\"status\":\"NEW\",\"customer\":{\"id\":\"c-7700\",\"name\":\"Dana Whitfield\",\"email\":\"dana.whitfield@example.com\",\"tier\":\"silver\",\"address\":{\"street\":\"Rue de la Loi 16\",\"city\":\"Brussels\",\"country\":\"BE\"}}}}" | python3 -c "import sys,json;print(json.load(sys.stdin)['processInstanceKey'])")
echo PI=$PI; echo $PI > $S/pi.txt; sleep 3
setvar crm-sync "$(cust x "Dana Whitfield" "dana.whitfield@example.com" gold "Rue de la Loi 16" Brussels)"
setvar shop-app '{"variables":{"chargeAmount":110}}'
setvar ai-assistant '{"variables":{"chargeAmount":104,"suggestion":"Apply 5% loyalty discount for gold tier"}}'
setvar demo '{"variables":{"chargeAmount":125,"status":"VALIDATED"}}'
setvar shop-app '{"variables":{"chargeAmount":119}}'
setvar crm-sync "$(cust x "Dana Whitfield" "d.whitfield@example.com" gold "Rue de la Loi 16" Brussels)"
setvar alice '{"variables":{"chargeAmount":140}}'
setvar ai-assistant '{"variables":{"chargeAmount":133,"suggestion":"Customer asked for express shipping, add surcharge"}}'
setvar shop-app '{"variables":{"chargeAmount":150,"status":"PAID"}}'
setvar demo "$(cust x "Dana Whitfield-Moreau" "d.whitfield@example.com" gold "Rue de la Loi 16" Brussels)"
setvar crm-sync "$(cust x "Dana Whitfield-Moreau" "d.whitfield@example.com" platinum "Avenue Louise 54" Brussels)"
setvar ai-assistant '{"variables":{"chargeAmount":142}}'
setvar alice '{"variables":{"chargeAmount":160}}'
JOB=$(api risk-worker -X POST $B/v2/jobs/activation -H "$J" -d '{"type":"check-risk","timeout":60000,"maxJobsToActivate":1,"requestTimeout":5000}' | python3 -c "import sys,json;print(json.load(sys.stdin)['jobs'][0]['jobKey'])")
api risk-worker -o /dev/null -X POST $B/v2/jobs/$JOB/completion -H "$J" -d '{"variables":{"score":119}}'; sleep 3
setvar demo '{"variables":{"riskScore":120,"status":"PACKED"}}'
setvar shop-app '{"variables":{"chargeAmount":175,"status":"SHIPPED"}}'
UT=$(api alice -X POST $B/v2/user-tasks/search -H "$J" -d "{\"filter\":{\"processInstanceKey\":\"$PI\"}}" | python3 -c "import sys,json;print(json.load(sys.stdin)['items'][0]['userTaskKey'])")
api alice -o /dev/null -X POST $B/v2/user-tasks/$UT/assignment -H "$J" -d '{"assignee":"alice"}'
api alice -o /dev/null -X POST $B/v2/user-tasks/$UT/completion -H "$J" -d '{"variables":{"approved":true,"chargeAmount":180}}'
sleep 5
api demo -X POST $B/v2/variables/history/search -H "$J" -d "{\"filter\":{\"processInstanceKey\":\"$PI\"}}" | python3 -c "
import sys,json,collections
items=json.load(sys.stdin)['items']
print(len(items),'changes')
print(dict(collections.Counter(i['actorType']+':'+str(i['actorId']) for i in items)))"
