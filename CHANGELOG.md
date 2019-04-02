<a name="0.17.0"></a>
## 0.17.0 (2019-04-02)


#### Breaking Changes

* **broker:**  
  * renamed payload to variables ([e2fed8bc](https://github.com/zeebe-io/zeebe/commit/e2fed8bc7d15092deb701cba5f0b158d250c2485))
  * input/output mappings and correlation key expressions access variables by name ([665393f0](https://github.com/zeebe-io/zeebe/commit/665393f0206c54cf0373cc7ac54edeec31b3f460))
* **clients:**  rename bufferSize to maxJobsActive ([2373c264](https://github.com/zeebe-io/zeebe/commit/2373c2649da13755e964f83a530d9adb3e6798ed))
* **json-el:**  conditions access variables by name instead of JSON path ([645297e8](https://github.com/zeebe-io/zeebe/commit/645297e84eb678707ea56a896421c1c1b3587314))
* **maven:**  prefix all maven artifacts with zeebe ([b2bac603](https://github.com/zeebe-io/zeebe/commit/b2bac603d565d581827dbcee1470d382a4e6e3b5))

#### Features

* **bpmn-model:**  reject end events with outgoing sequence flows ([50582295](https://github.com/zeebe-io/zeebe/commit/50582295a9b7588c27798173a7135398b7239357))
* **broker:**  
  * add workflow key to variable record ([624d32a2](https://github.com/zeebe-io/zeebe/commit/624d32a2fc3f3c9941fbe2603cb32e1db1a6ac9d))
  * input/output mappings and correlation key expressions access variables by name ([665393f0](https://github.com/zeebe-io/zeebe/commit/665393f0206c54cf0373cc7ac54edeec31b3f460))
* **exporter:**  export error record ([b35728bf](https://github.com/zeebe-io/zeebe/commit/b35728bfe3542a6c5beccea5dcfd290c02853d2e))
* **json-el:**  conditions access variables by name instead of JSON path ([645297e8](https://github.com/zeebe-io/zeebe/commit/645297e84eb678707ea56a896421c1c1b3587314))
* **protocol:**  introduce new ErrorRecord ([21ca50a8](https://github.com/zeebe-io/zeebe/commit/21ca50a8ac065b7f33daa6908d83f7924668679b))

#### Bug Fixes

* **broker:**
  *  prevent unnecessary message subscription closing ([37a03bf0](https://github.com/zeebe-io/zeebe/commit/37a03bf06458f0e710b592ca74c256414283a4bd))
  *  prevent NPE in delayed message subscription closing ([d733cee5](https://github.com/zeebe-io/zeebe/commit/d733cee5c882ae1e9bc3e7d93e32d9c05bb948c4))
  *  renamed payload to variables ([e2fed8bc](https://github.com/zeebe-io/zeebe/commit/e2fed8bc7d15092deb701cba5f0b158d250c2485))
  *  reset reused event scope instance ([f9c3e7f3](https://github.com/zeebe-io/zeebe/commit/f9c3e7f363d8be16e9cbd7001f48d8ed23821f56))
  *  stop iterating jobs if job batch reached max capacity ([91f276d9](https://github.com/zeebe-io/zeebe/commit/91f276d954df4e68c665c584e0c737fa716b3e63))
  *  increase gateway transport buffer to default 64 megabyte ([fbf07e29](https://github.com/zeebe-io/zeebe/commit/fbf07e29562f47d77e18e32d34eda829b445bd56))
* **clients:**  rename bufferSize to maxJobsActive ([2373c264](https://github.com/zeebe-io/zeebe/commit/2373c2649da13755e964f83a530d9adb3e6798ed))
* **clients/java:**  clarify outdated update job retries java doc ([5881187f](https://github.com/zeebe-io/zeebe/commit/5881187f619e0addd9aa81c7f7f30d3fc3bb94f0))
* **exporters/elasticsearch-exporter:**  migrates ES to pkg io.zeebe.exporter ([7106f167](https://github.com/zeebe-io/zeebe/commit/7106f16755c52d32ffc8709d5a83e3c6ba4b067b))
* **logstreams:**
  *  removed StreamProcessorControllerTest race condition ([3ad0e3ac](https://github.com/zeebe-io/zeebe/commit/3ad0e3ac05304f5aa6f114e1568e9c2a013bf647))
  *  removed race condition from LogBlockIndexWriterTest ([134c5b1b](https://github.com/zeebe-io/zeebe/commit/134c5b1b424ca123e6e079d385544f64c6387327))
* **maven:**  prefix all maven artifacts with zeebe ([b2bac603](https://github.com/zeebe-io/zeebe/commit/b2bac603d565d581827dbcee1470d382a4e6e3b5))



<a name="0.16.4"></a>
## 0.16.4 (2019-03-22)


#### Bug Fixes

* **broker:**  use default RocksDB column family options ([4393958b](https://github.com/zeebe-io/zeebe/commit/4393958b76f115a565d38ec8b92af66d75215a00))


<a name="0.16.3"></a>
## 0.16.3 (2019-03-15)


#### Bug Fixes

* **broker:**
  *  stop iterating jobs if job batch reached max capacity ([cc90c04a](https://github.com/zeebe-io/zeebe/commit/cc90c04a3e0e5d625c1cae36d4ae3c9bd446a579))
  *  increase gateway transport buffer to default 64 megabyte ([449ea10e](https://github.com/zeebe-io/zeebe/commit/449ea10e61712d768cccb166ecc8f5ed79e2f71d))



<a name="0.16.2"></a>
## 0.16.2 (2019-03-11)


#### Bug Fixes

* **broker:**
  * update workflow instance created metric correctly ([a627ea78](https://github.com/zeebe-io/zeebe/commit/a627ea78d09dec22cdc0474165e8a7e37055a2e1))
  * time out trigger respects dispatcher backpressure ([41037895](https://github.com/zeebe-io/zeebe/commit/4103789534bcc75a97a795a102580ba8ec2665ed))

#### Features

* **broker:**  add HTTP metrics endpoint ([131a57fc](https://github.com/zeebe-io/zeebe/commit/131a57fc27cff00417a677d6e4ddfa8cffd9940a))



<a name="0.16.1"></a>
## 0.16.1 (2019-03-07)


#### Features

* **broker:**  condition expression is part of incident message ([447091aa](https://github.com/zeebe-io/zeebe/commit/447091aa3df1c91a17c6f4fd3ef9800f894a7078))
* **clients/go:**  replace update payload with set variables ([49ae37ab](https://github.com/zeebe-io/zeebe/commit/49ae37abc1f54eb6f68e9a5b8621b818802b2dc1))
* **exporters/elasticsearch-exporter:**  adds basic HTTP auth to exporter ([791ea802](https://github.com/zeebe-io/zeebe/commit/791ea802d682fcc15678340d29163043028038fc))

#### Bug Fixes

* **broker:**
  *  set correct license information in pom file ([a6bd1ec8](https://github.com/zeebe-io/zeebe/commit/a6bd1ec8b81c7dae24c805b44f87ceb135dc6edd))
  *  send close message subscription command to correct partition ([b5600170](https://github.com/zeebe-io/zeebe/commit/b56001709b4693acd037f92e4724119a991cf2ce))
  *  set correct BPMN element type for sequence flows ([74f93a3e](https://github.com/zeebe-io/zeebe/commit/74f93a3e9654c89309a79c6ccf47e6a23081859d))
  *  set repetitions and reset timer record ([418eedc9](https://github.com/zeebe-io/zeebe/commit/418eedc9be1a8f3a827d4f9cf81880ca0ef145d3))
  *  only distribute deployments from first partition ([a2a069c9](https://github.com/zeebe-io/zeebe/commit/a2a069c9e70430892c2418710bf1c58ee75b9b76))
  *  fix timer start events key ([833f3246](https://github.com/zeebe-io/zeebe/commit/833f3246f668537044587fc3cb255c9789d9bfae))
  *  fix overflows ([5833ee56](https://github.com/zeebe-io/zeebe/commit/5833ee563aa30300bd34daf6b38bad225bf8968d))
  *  stop iterating after prefix was exceeded ([c5c38ba9](https://github.com/zeebe-io/zeebe/commit/c5c38ba9a3cb840ea7c7105376c2d7b9a263e23d))



<a name="0.15.1"></a>
## 0.15.1 (2019-02-26)


#### Bug Fixes

* **broker:**  stop iterating after prefix was exceeded ([0e873c6a](https://github.com/zeebe-io/zeebe/commit/0e873c6a90708be762ca40119f839d3efef2cb87))



<a name="0.15.0"></a>
## 0.15.0 (2019-02-12)


#### Bug Fixes

* **broker:**
  *  reject deployment which contains at least one invalid resource ([fd478adc](https://github.com/zeebe-io/zeebe/commit/fd478adc2691b427c54389461c6f4bdd685d7267))
  *  ensure YAML transformation does not break the msgpack serialization ([575178cf](https://github.com/zeebe-io/zeebe/commit/575178cf87a05fff1db6ecc8eb31d89a94a547b5))
  *  limit jobs in JobBatchRecord to 65Kb ([6324a8e5](https://github.com/zeebe-io/zeebe/commit/6324a8e5b905cfc10b38e8688f94cb381b132ff4))
  *  reject create workflowinstance command when there is no none start event ([7651fcc7](https://github.com/zeebe-io/zeebe/commit/7651fcc73cbe50639e60fb40caf3c9c6fe06b8fb))
  *  expand log block index if capacity is reached ([1b6adfeb](https://github.com/zeebe-io/zeebe/commit/1b6adfeb5060dd4ae6b1ba27d6b706898e4b9b15))
  *  fix state interference with processor ([5b948b09](https://github.com/zeebe-io/zeebe/commit/5b948b09dcbe19b4fdae5e0d98bd5f727d7855fb))
  *  ignore empty or invalid task headers ([7cb055c8](https://github.com/zeebe-io/zeebe/commit/7cb055c8c858477f84b05516eca74a0b0e1aff2f))
  *  reject empty correlation key ([879ae05a](https://github.com/zeebe-io/zeebe/commit/879ae05a35d7b582ff6158560917d32f566ec616))
  *  event-based gateways can't be triggered twice ([e5c38366](https://github.com/zeebe-io/zeebe/commit/e5c3836613203f43c98e5c3415278094a4c4399e))
  *  prevent canceling of element instances ([a9b16ae0](https://github.com/zeebe-io/zeebe/commit/a9b16ae05266fed49569ffc676ac3576b2a126cc))
  *  don't open subscription if incident is raised ([6e2f3671](https://github.com/zeebe-io/zeebe/commit/6e2f3671862b85dfa2dbf833f71674898b9ee483))
  *  save the last exported record position before closing the state * ensure the RocksDB directory is deleted ([cd912d8f](https://github.com/zeebe-io/zeebe/commit/cd912d8fb3e2ebb378bc34570f4484cfdbd159f2))
  *  close gateway before service containers ([cd74ac24](https://github.com/zeebe-io/zeebe/commit/cd74ac24911b94931d31c78ed38cae8b0171436a))
  *  ignore unreferenced messages in transformer ([6c9f1035](https://github.com/zeebe-io/zeebe/commit/6c9f1035b3e33eee7b0cedaa4554ea206d7d3753))
  *  Convert numeric correlation key to string ([953ec7b7](https://github.com/zeebe-io/zeebe/commit/953ec7b7632f77205bd5ae7334f38b427a932009))
  * add explicit endianness in logstream ([d8dbd667](https://github.com/zeebe-io/zeebe/commit/d8dbd6670dea805ba1154f8a91cbeb6987f601ad))
  * avoid overflow on String16 type ([bf2fb4cd](https://github.com/zeebe-io/zeebe/commit/bf2fb4cdd2dcfa10b91b0dcde05160def289a5f6))

#### Features

* **broker:**
  *  add workflowInstanceKey to variable records ([35b2f1e4](https://github.com/zeebe-io/zeebe/commit/35b2f1e45351d29109a2fef0810116ca6661f72f))
  *  unify lifecycle of flow elements ([7c2589ab](https://github.com/zeebe-io/zeebe/commit/7c2589abf5ca310e8fc3909cc2b8c042e45db55a))
  *  improve error reporting in gRPC API ([c02e6d80](https://github.com/zeebe-io/zeebe/commit/c02e6d801a1cb2748767b36a718b31e8cca5fbf1))
  *  added bpmn element type to WorkflowInstanceRecord ([09e7e0c5](https://github.com/zeebe-io/zeebe/commit/09e7e0c5b4159185a475b3b341d7c4bfa45b360b))
  *  export variable records to elasticsearch ([f96aa8fc](https://github.com/zeebe-io/zeebe/commit/f96aa8fce13ba9c94f083195d92cc791ecebc6bc))
  *  consistent and meaningful error message format ([ff34a27a](https://github.com/zeebe-io/zeebe/commit/ff34a27ae719ae42ade4248f1a7e17cae749022e))
  *  add list of variables to be fetched on activation to protocol ([7a7d63ca](https://github.com/zeebe-io/zeebe/commit/7a7d63ca8835602e4203d437259eef342b8b7640))
  *  added support for timeDate timer definitions ([ae1823a0](https://github.com/zeebe-io/zeebe/commit/ae1823a0d4dd2d2a0aa10f5a7bd4a418644e165c))
  *  support timer start event ([f28c318b](https://github.com/zeebe-io/zeebe/commit/f28c318b25c3ffa6654016dae45e8fe12a4a9149))
  *  support non-interrupting message boundary events ([4fa149fb](https://github.com/zeebe-io/zeebe/commit/4fa149fbaf29bef34e30001b9fa28573bd9e7d39))
  *  support message start events ([2364e08c](https://github.com/zeebe-io/zeebe/commit/2364e08cdcd8f59a87b37c51cb9db7d9e4d84a42)) ([157e66e2](https://github.com/zeebe-io/zeebe/commit/157e66e21ec135216e3f69724395c14fe6849c2a))
  *  write variable events ([40b32063](https://github.com/zeebe-io/zeebe/commit/40b3206329e1e19c18f98d557eb3403dfebe6d3b))
  *  propagate variables on presence of out mappings ([64f45fbe](https://github.com/zeebe-io/zeebe/commit/64f45fbe29ba51417e75c848c730569ba47d0c1e))
  *  allow updating job retries in all job states ([6e056f7f](https://github.com/zeebe-io/zeebe/commit/6e056f7f21c099dc26dfa61a554eb3b13f0f724f))
  *  support non-interrupting timer boundary events ([84313356](https://github.com/zeebe-io/zeebe/commit/8431335624d02941e90b568c13e74b847f65f3a7))
  * allow non-strict condtions ([c8f4480d](https://github.com/zeebe-io/zeebe/commit/c8f4480d707048ad9ae4dfcda685483485cda2e0))
  *  provide integration test utitilities for exporter authors ([5cbbcfc9](https://github.com/zeebe-io/zeebe/commit/5cbbcfc948b7d16e6a9f7f4641774675f60ac219))
  *  adds a slew of Exporter test utilities ([899221ea](https://github.com/zeebe-io/zeebe/commit/899221ead4b03a2c017fcb410ba22bc4367a6985))
* **clients/go:**
  * add optional list of variables to activate jobs/job worker ([911aeea5](https://github.com/zeebe-io/zeebe/commit/911aeea5f08bcd02239236d18b140a43c1031f4a))
  * Add omitempty-ignoring object marshaller method to go api ([4823b42b](https://github.com/zeebe-io/zeebe/commit/4823b42b865bdc4e14be96c6d55cdd4e6dc4502b))
* **clients/java:**
    *  add optional list of variables to activate jobs/job worker ([6eabff25](https://github.com/zeebe-io/zeebe/commit/6eabff250e1226ac197a0c5f2c327f80e79bc99a))
    *  propagate job worker exception message to broker ([23a70ea8](https://github.com/zeebe-io/zeebe/commit/23a70ea83a69fd9005dd5ebd38403b18f18939aa))
* **clients/zbctl:**  set error message in fail command to stderr content ([8f8b3889](https://github.com/zeebe-io/zeebe/commit/8f8b3889079559a83a5ec3cdc83fa40f80321f3b))


<a name="0.14.0"></a>
## 0.14.0 (2018-12-04)


#### Bug Fixes

* **broker-core:**
  *  update job state on update retries ([8c0a424b](https://github.com/zeebe-io/zeebe/commit/8c0a424b020651d653b5dedd2387b601b037ff5a))
  *  fix xor incident resolving ([ab592682](https://github.com/zeebe-io/zeebe/commit/ab5926822b3beaa404d1e8e008b80ed30e4462af))
  *  fix job state on cancel command ([024aafe3](https://github.com/zeebe-io/zeebe/commit/024aafe3152bac6605ef4526b3fa97e59fccee6c))
* **clients/java:**
  *  dont break cause chain ([be010d0e](https://github.com/zeebe-io/zeebe/commit/be010d0e0fcd898a91a5456cbe49d2912ccb34bd))
  *  fix default job poll interval and message ttl ([ab714eaa](https://github.com/zeebe-io/zeebe/commit/ab714eaaa7265d01c8da4bc861490fa512de6082))
* **exporters/elasticsearch:**  delayed flush should ignore bulk size ([beb15efc](https://github.com/zeebe-io/zeebe/commit/beb15efc2a7fa9c181cb9d7de017d93e27bccdff))

#### Features

* **bpmn-model:**
  *  enable non-interrupting time cycle boundary events ([bcf57f5e](https://github.com/zeebe-io/zeebe/commit/bcf57f5eba3dad93fe2ef8bc99926841f1d2624b))
  *  enable interrupting message boundary events ([ff1bddcd](https://github.com/zeebe-io/zeebe/commit/ff1bddcd44386f15a081139af197950b45d85588))
  *  enable interrupting timer boundary events ([edb345d1](https://github.com/zeebe-io/zeebe/commit/edb345d1138dc37d555f882ea99e02e1b5488f5e))
* **broker-core:**
  *  add support for event-based gateway ([425d6ca4](https://github.com/zeebe-io/zeebe/commit/425d6ca421cab2c09d1ba7cd8d41cb9ceb6ff16e))
  *  add support for interrupting message boundary events ([ea442ee9](https://github.com/zeebe-io/zeebe/commit/ea442ee9166dd8759b4880060efb5f176136279c))
  *  add job error message to failed jobs ([7baff1b7](https://github.com/zeebe-io/zeebe/commit/7baff1b79e97b3e60dd33c71100141535ddc66ba))
  *  impl new incident concept ([2a26ff58](https://github.com/zeebe-io/zeebe/commit/2a26ff58fc2b69a7d56d4862d5f59ec8ce87a4b6))
  *  add support for interrupting timer boundary events ([910b7b78](https://github.com/zeebe-io/zeebe/commit/910b7b78ba8167f2d1faec727e1f1cb21178f89c))
* **clients/go:**
  *  add resolve incident command ([e1a850db](https://github.com/zeebe-io/zeebe/commit/e1a850db1fe5023f1669a3555c2a0e7ff3cf6064))
  *  add error message to fail job command ([32bc691d](https://github.com/zeebe-io/zeebe/commit/32bc691db77a884ce5f93138059e84ef434c71bc))
* **clients/java:**  add resolve incident command ([25c1df38](https://github.com/zeebe-io/zeebe/commit/25c1df38a834032036cfbff77d38b3962062c13d))
* **clients/zbctl:**
  *  add resolve incident command ([3360289b](https://github.com/zeebe-io/zeebe/commit/3360289bb945c876e8a68d9684f0ac68934d376e))
  *  add fail job error message flag ([83269d8c](https://github.com/zeebe-io/zeebe/commit/83269d8cfbb50b09940860692d7f2c4085ad311e))
  *  implement publish message command ([1abca40a](https://github.com/zeebe-io/zeebe/commit/1abca40a7a96b9c7b92662bc35a74b4252f95771))
* **gateway:**  add incident resolve request to gateway ([e2eca8d2](https://github.com/zeebe-io/zeebe/commit/e2eca8d21c94ff82390eb2c9e24a2ad033db722d))



<a name="0.13.0"></a>
## 0.13.0 (2018-11-06)


#### Bug Fixes

* **broker-core:**
  *  exclusive split when default flow is first in XML ([75cd1539](https://github.com/zeebe-io/zeebe/commit/75cd1539c730440a87c2fefeaa96c253b5e856ac))
  *  add null check in job state controller ([10496ae7](https://github.com/zeebe-io/zeebe/commit/10496ae7b32f89e50c43896039fdc74a0c3f8b4d))
  *  correlate a message only once per wf instance ([892357cc](https://github.com/zeebe-io/zeebe/commit/892357cc35dd21d4d50c27ecf3edb5005f9d9809))
  *  fix concurrency problems with request metadata ([34b6b6fa](https://github.com/zeebe-io/zeebe/commit/34b6b6fa28e4552bfddf38f1c1e45457bb7a08ca))
* **exporters/elasticsearch:**  use correct index delimiter in root template ([e6c62be8](https://github.com/zeebe-io/zeebe/commit/e6c62be8df476cbd55ac5be0673633f4f35b3ec0))
* **gateway:**  use resource type provided in request instead of detecting ([9fbaccb5](https://github.com/zeebe-io/zeebe/commit/9fbaccb5965b211db6716ee8b2e1a81741708aa6)))

#### Breaking Changes

* **gateway:**  use resource type provided in request instead of detecting ([9fbaccb5](https://github.com/zeebe-io/zeebe/commit/9fbaccb5965b211db6716ee8b2e1a81741708aa6))

#### Features

* **broker-core:**
  *  add debug http exporter ([ef2d0203](https://github.com/zeebe-io/zeebe/commit/ef2d02035c09bdc4577551651aace7a20fb07450))
  *  handle intermediate timer catch event ([62111c35](https://github.com/zeebe-io/zeebe/commit/62111c3549357401bf20c1dec8620375837ebafe))
* **clients/go:**
  *  implement polling job worker ([09a21788](https://github.com/zeebe-io/zeebe/commit/09a21788b6c794c7ae1448230edfb0adc83d69ba))
  *  implement list workflows and get workflow ([5169ac27](https://github.com/zeebe-io/zeebe/commit/5169ac27c6448d083540dc7fe92c26a40b315711))
* **clients/zbctl:**
  *  add create worker command ([68d17600](https://github.com/zeebe-io/zeebe/commit/68d176008a9d22ad3e703bc43f1a241698ee47af))
  *  implement list workflows and get workflow ([429bdc47](https://github.com/zeebe-io/zeebe/commit/429bdc472de68d55ebdf43b7ad8bdc52d0ddd525))
  *  allow to configure the address to connect to ([0a3a4010](https://github.com/zeebe-io/zeebe/commit/0a3a401059b5f8128f9c746f9fa0b403f1ff6875))
* **dist:**  add standalone gateway script and configuration ([df212f12](https://github.com/zeebe-io/zeebe/commit/df212f12492d187d749382b67da346b29f4a694d))
* **gateway:**  add gateway configuration readable from toml file ([08b66441](https://github.com/zeebe-io/zeebe/commit/08b6644132e41b398d850408a35de78dbf654f98))
* **gateway-protocol:**  expose cluster settings in gateway protocol ([0035d39d](https://github.com/zeebe-io/zeebe/commit/0035d39d1bd2c3dce577551d19394ba484f58ade))



<a name="0.12.1"></a>
## 0.12.1 (2018-10-26)


#### Bug Fixes

* **broker-core:**
  *  exclusive split when default flow is first in XML ([3c91aa1d](https://github.com/zeebe-io/zeebe/commit/3c91aa1dc4f4740f50c3661cd87d8d565fae8ef3))
  *  fix concurrency problems with request metadata ([85e26e92](https://github.com/zeebe-io/zeebe/commit/85e26e926d131f23e8e5d9a28fac3a1e8da0f367))
* **exporters/elasticsearch:**  use correct index delimiter in root template ([9572500a](https://github.com/zeebe-io/zeebe/commit/9572500a0c889028e9966d0b31941092f3789caa))



<a name="0.12.0"></a>
## 0.12.0 (2018-10-16)


#### Bug Fixes

* **bpmn-model:**
  *  do not return raw type on connectTo ([9b45432a](https://github.com/zeebe-io/zeebe/commit/9b45432afab2dcef7d69588e1bbe31a27dc44e59))
  *  return typed builders from move to methods ([3cb70e08](https://github.com/zeebe-io/zeebe/commit/3cb70e089263fd31fd4a8a089de8b04cbd4115f5))
* **broker-core:**
  *  fix persistence cache ([eebc90f4](https://github.com/zeebe-io/zeebe/commit/eebc90f48128d24dd32a4adb45f4a7eb0bc47d8c))
  *  remove message subscription completely ([573ea845](https://github.com/zeebe-io/zeebe/commit/573ea845dd44ddfd846f832ad73388eaaf39dd94))
  *  fix unstable test ([7982444b](https://github.com/zeebe-io/zeebe/commit/7982444bdc572948c3a0dba5c27b7d0dca63ae7e))
  *  fix recovery ([cee0d1a4](https://github.com/zeebe-io/zeebe/commit/cee0d1a44036c30149ca90558f475f4783f7cddb))
  *  fix offset assert in messages ([efa4d563](https://github.com/zeebe-io/zeebe/commit/efa4d5630767d8f6f7080f23d0c7f92dfcd7650e))
  *  fix reprcoessing of deployments ([8cfff928](https://github.com/zeebe-io/zeebe/commit/8cfff92855d9e17f5b581dfd435a8acf9eacba4d))
  *  set flow scope payload after output mapping ([30ef6d69](https://github.com/zeebe-io/zeebe/commit/30ef6d69d1c52e19e37701bc418d73cfdd25a2d2))
  *  fix state byte ordering ([c7b59c4b](https://github.com/zeebe-io/zeebe/commit/c7b59c4b6a8426ae2e95fc0b923c4b01261d9ecf))
  *  move standalone broker into dist ([a26bf9a3](https://github.com/zeebe-io/zeebe/commit/a26bf9a39c9ce3771e08efae9c8c31c19b546411))
  *  messages with same name and correlation key ([366022a7](https://github.com/zeebe-io/zeebe/commit/366022a74ae52ae85d84b2bf18bb8774e88a221b))
  *  find message subscription ([38bb023e](https://github.com/zeebe-io/zeebe/commit/38bb023eefba7cb5fe26600612c3367117af7bef))
  *  reliably activate jobs ([8947ebfb](https://github.com/zeebe-io/zeebe/commit/8947ebfba76244866338408fadef092006e48621))
  *  message stream processors work with multiple partitions ([585c3615](https://github.com/zeebe-io/zeebe/commit/585c36156b8bd1283be369ee271410abd72299c7))
  *  snapshot replication on leader change ([64a0ff72](https://github.com/zeebe-io/zeebe/commit/64a0ff72dcc2b641ca93ca0bb86b7e8ba296721c))
* **clients/go:**  add missing retries parameter to fail job command ([f772eb55](https://github.com/zeebe-io/zeebe/commit/f772eb55cae05537dc344b62dd302960d616bcca))
* **dispatcher:**  decrease total work ([8136cdef](https://github.com/zeebe-io/zeebe/commit/8136cdef28e4e9cc5ec08f8de2678153a7114211))
* **dist:**  remove semicolon from cfg ([e617f378](https://github.com/zeebe-io/zeebe/commit/e617f378f0585759f03f2e7d002d395883e16ac2))
* **exporter:**  add aliases to ES indices ([7e9fffef](https://github.com/zeebe-io/zeebe/commit/7e9fffef7160c9aecbddaf2495baca6386affef5))
* **gateway:**  use NonBlockingMemoryPool to avoid long timeouts ([17e2f0aa](https://github.com/zeebe-io/zeebe/commit/17e2f0aac92f8cc0f0c6133e3162d84824edb6c5))
* **job:**  rewirte job append ([3167d302](https://github.com/zeebe-io/zeebe/commit/3167d302ae107bae39d6926d30ef396fa582eebf))
* **logstreams:**  do not close twice ([9a075c74](https://github.com/zeebe-io/zeebe/commit/9a075c74090a7fbad8c6629074ca4c80f8c9fa2f))
* **raft:**  persist raft members list on every member change ([dfa958a8](https://github.com/zeebe-io/zeebe/commit/dfa958a849160f1a3b43eb0ba22938331e4de845))
* **scheduler:**  handle race condition where job is added to dropped queue ([3304862f](https://github.com/zeebe-io/zeebe/commit/3304862f6dc69ce797b9053e300e3535724fee5a))
* **transport:**  retry send message if channel is not open ([ebb718d3](https://github.com/zeebe-io/zeebe/commit/ebb718d3e6323eebbdd93de73d29f2273e8505b9))

#### Features

* **bpmn-model:**
  *  extensions for payload mapping ([a4732a2f](https://github.com/zeebe-io/zeebe/commit/a4732a2fd3f5e2c5b8173951ca8f1158b99aca36))
  *  validate supported elements and event definitions ([950038cf](https://github.com/zeebe-io/zeebe/commit/950038cfc4eccb373e9ed4c4937d6cb764f4baa0))
  *  support intermediate catch event and receive task ([6741b6f7](https://github.com/zeebe-io/zeebe/commit/6741b6f7fdac6173ebbefdc8c74c4804ba152d4c))
* **broker-core:**
  *  implicit parallel split ([4eff3a66](https://github.com/zeebe-io/zeebe/commit/4eff3a660a46f83a1543cd3d5e452a2496052623))
  *  complete job by key ([5835cf2f](https://github.com/zeebe-io/zeebe/commit/5835cf2f69c6039d8eb4b36d15f8db35a39e6d95))
  *  fail jobs by key and retries ([1c08141e](https://github.com/zeebe-io/zeebe/commit/1c08141e4548b118c93ec34df50735fa1e34d115))
  *  update job retries by key ([d15bfd50](https://github.com/zeebe-io/zeebe/commit/d15bfd50f17a3c315e638caa4861bb6be603cf6b))
  *  payload update with key and payload only ([afa99c45](https://github.com/zeebe-io/zeebe/commit/afa99c45d964762de9348ff6b3828b2135ead938))
  *  workflow instance cancellation by key ([c4c78479](https://github.com/zeebe-io/zeebe/commit/c4c78479c9b39bd8864a86b8a0d14a493d0ea8fb))
  *  merge token payloads on scope completion ([9d42e78a](https://github.com/zeebe-io/zeebe/commit/9d42e78ad636640c7c08941ea58ffad00a6e34c4))
  *  merge payloads on parallel gateway merge ([2c7370e3](https://github.com/zeebe-io/zeebe/commit/2c7370e3a00059878955f768e0caedea9fc04d4c))
  *  introduce message state controller ([92ed1264](https://github.com/zeebe-io/zeebe/commit/92ed126478d03b7906f713dee0119712c5480813))
  *  BPMN merging parallel gateway ([83743595](https://github.com/zeebe-io/zeebe/commit/83743595ddec7fdfd7161ddc00e3792456ccdfc6))
  *  install partitions via cfg ([c961adf7](https://github.com/zeebe-io/zeebe/commit/c961adf7b91d851c4d64417646101baa190fc99a))
  *  create partitions matrix ([497a2898](https://github.com/zeebe-io/zeebe/commit/497a2898eadf3502d4ef09be04d55f8912faa36e))
  *  forking parallel gateway ([d40aa3ec](https://github.com/zeebe-io/zeebe/commit/d40aa3eca31099ab7207bc875fe934efeb49847c))
  *  add debug exporter ([5e8ca251](https://github.com/zeebe-io/zeebe/commit/5e8ca2516a71a6a5976880288b37e22dc30af3e3))
  *  create partition ids in cluster cfg ([039a5798](https://github.com/zeebe-io/zeebe/commit/039a57981c202bbcda705b4870d401a26ef150a0))
  *  allow to set data directories as environment variable ([3eedf6ea](https://github.com/zeebe-io/zeebe/commit/3eedf6ead00790844f1d5106418c3406f58dee8a))
  *  allow to set initial contact points as environment variable ([ab7cfda6](https://github.com/zeebe-io/zeebe/commit/ab7cfda620e962a433c0bdc51eba8bb907b5932a))
  *  allow to set host as environment variable ([b8f5131e](https://github.com/zeebe-io/zeebe/commit/b8f5131ece978edca79010dd31d33f9ad6e754ba))
  *  add exporter manager service ([f6f71d0b](https://github.com/zeebe-io/zeebe/commit/f6f71d0b066b8df729ffeb951f5b2790f262dfc1))
  *  add node id to configuration ([137c5621](https://github.com/zeebe-io/zeebe/commit/137c5621df9f0708511d0fb0ca168b1a17244259))
  *  correlate message resilient ([0f850910](https://github.com/zeebe-io/zeebe/commit/0f850910070424b0cccb989ed8d6c400f6e5fc9a))
  *  open a message subscription when a receive task is entered ([eec02854](https://github.com/zeebe-io/zeebe/commit/eec02854febc592868a96affff5e96b15694762b))
  *  open message subscription resilient ([816e2c0c](https://github.com/zeebe-io/zeebe/commit/816e2c0c90ecfcc6214153b744dda2404e200229))
  *  publish message with TTL ([de7d7604](https://github.com/zeebe-io/zeebe/commit/de7d7604d7f92dfd7ad0e0999b1322dd3e705977))
  *  embedded subprocess ([e54c7070](https://github.com/zeebe-io/zeebe/commit/e54c7070790a8999d05ff62e1984cb487e603fca))
  *  correlate a message to all subscriptions ([60fd1ae1](https://github.com/zeebe-io/zeebe/commit/60fd1ae1a8df080d3cc4c53269aae4b86547035e))
  *  open message subscription when catch event is entered ([9223947d](https://github.com/zeebe-io/zeebe/commit/9223947dbda91c97ac5a49da889e51133b073bdc))
  *  a message can be published idempotent ([dafc5294](https://github.com/zeebe-io/zeebe/commit/dafc529448e0681412383805d4381915b4456a41))
  *  a message can be published ([ac43219d](https://github.com/zeebe-io/zeebe/commit/ac43219db4d1c9542d0b88b06172d8ae3efeb62a))
  *  add port offset network configuration parameter ([3e5755d9](https://github.com/zeebe-io/zeebe/commit/3e5755d9f1fb1186ed5fe17d4783493783814e55))
  *  handle fetch-created-topics request ([1a20e311](https://github.com/zeebe-io/zeebe/commit/1a20e31178cc8e7abbb7b8ad8f264f5394797205))
  *  implement batch job activation ([5f3920ab](https://github.com/zeebe-io/zeebe/commit/5f3920ab669fa85d8324345a5e32dabb73c64c58))
  *  make keys global unique ([47053f38](https://github.com/zeebe-io/zeebe/commit/47053f382b18e8420a4fa68f725254668911f424))
  *  migrate instance index to RocksDb ([23a5d036](https://github.com/zeebe-io/zeebe/commit/23a5d036bf100de68477ddf5313a7a658569cdfa))
  *  migrate workflow cache ([0ed3275d](https://github.com/zeebe-io/zeebe/commit/0ed3275dee1e8e585f88e004dc054054760867c3))
  *  migrate workflow repository ([5433844e](https://github.com/zeebe-io/zeebe/commit/5433844e3347260dc0af5aa2908f4a135f42d8f4))
  *  configure cluster via cfg ([3d5e6271](https://github.com/zeebe-io/zeebe/commit/3d5e6271aae1ff7f80b862fe5b348cca4f1bd662))
  *  push deployment to remaining partitions ([38c699e5](https://github.com/zeebe-io/zeebe/commit/38c699e5bcf17c767e9c941273e1bf35d1ddb00c))
  *  change zeebe ports ([c0bd61c0](https://github.com/zeebe-io/zeebe/commit/c0bd61c095ad6e2fa0ad5529733f49d97dc9a252))
  *  migrates JobInstanceStreamProcessor to use RocksDB for state ([87182d3c](https://github.com/zeebe-io/zeebe/commit/87182d3cdc841a62175afd17a482f38d5169a8cd))
  *  integrates RocksDB as state backend for stream processors ([53ff0b5d](https://github.com/zeebe-io/zeebe/commit/53ff0b5d3d57e8dcc38ffd7c45c6d1f76a785da1))
* **clients/go:**
  *  implement activate jobs command ([2e16bea6](https://github.com/zeebe-io/zeebe/commit/2e16bea66387fcbb1926a9771683a25092e8da68))
  *  implement update job retries ([b3b763b3](https://github.com/zeebe-io/zeebe/commit/b3b763b33e858c1994697013fc401725770d34dc))
  *  implement fail job ([dcc1860f](https://github.com/zeebe-io/zeebe/commit/dcc1860fc50ff59b7d40b8d4aef4bc5523fe344b))
  *  implement complete job ([adb698e3](https://github.com/zeebe-io/zeebe/commit/adb698e375c587be1f7f49bc8f1dd6bee390678d))
  *  implement update payload ([258e1eac](https://github.com/zeebe-io/zeebe/commit/258e1eac1d8855bab7a61b7e6ebbfd7f26bc2ad4))
  *  Added simple golang client with health check ([74373755](https://github.com/zeebe-io/zeebe/commit/74373755974c20431204548f5d34ec5b82637dcc))
  *  Added automatic generation of Golang proto code ([5ff13ae2](https://github.com/zeebe-io/zeebe/commit/5ff13ae2e39aa9379ede2357748330e1f76c48fd))
  *  Added cancel workflow instance ([4838572d](https://github.com/zeebe-io/zeebe/commit/4838572df42cdadd69943d6154b2e5ec607a3bab))
  *  Added create job rpc on golang client ([725ce609](https://github.com/zeebe-io/zeebe/commit/725ce6097f6a0fcf912b2166b3712d5b6be2a4ab))
  *  Added golang client and fixed tests ([3bbd7cfc](https://github.com/zeebe-io/zeebe/commit/3bbd7cfc20a615624e7596b0552f65be13e456ea))
  *  Added publish message rpc on golang client ([5169ddf0](https://github.com/zeebe-io/zeebe/commit/5169ddf07244f333da8c1b3b7d4dca1ea0a9813a))
* **clients/java:**
  *  publish message with java client ([aafd8ba3](https://github.com/zeebe-io/zeebe/commit/aafd8ba33ce52a53d5df95dbe652991d4030bdcf))
  *  Java client switched to use client stub ([8e4a0bcc](https://github.com/zeebe-io/zeebe/commit/8e4a0bcce379f749ea68886d5c4f4fe470f5f989))
  *  implement polling job workers ([1134fc7d](https://github.com/zeebe-io/zeebe/commit/1134fc7dcc8c116c6fbfe3b93ebe095d36e8c531))
  *  implement get workflow and list workflows ([764398a1](https://github.com/zeebe-io/zeebe/commit/764398a142c7fa01f11dc4fef731e81b13ac6c32))
  *  complete job by key ([8030a88c](https://github.com/zeebe-io/zeebe/commit/8030a88c9adc42946d04e42cf8e041c91c742ffd))
  *  fail job with key and retries ([7f66d441](https://github.com/zeebe-io/zeebe/commit/7f66d44153f957effceeb19f2ecf2e9ed40678a0))
  *  update job retries by key ([434b6364](https://github.com/zeebe-io/zeebe/commit/434b63647fd606b4f2df674744e24cbd19d85234))
  *  update payload request ([468002a2](https://github.com/zeebe-io/zeebe/commit/468002a23d2321f08627ed1abbd49f21f99ac063))
  *  cancel workflow instance by key ([314ef859](https://github.com/zeebe-io/zeebe/commit/314ef859134a4dd9e3f6ea653bdd2f965a272814))
  *  create workflow instance request ([edcfbb02](https://github.com/zeebe-io/zeebe/commit/edcfbb02743566cd4b7ede7643082237db247f0f))
  *  add create job request ([c45fc3d1](https://github.com/zeebe-io/zeebe/commit/c45fc3d1b23f2e427cdb22786c670f14cd2e45a0))
* **clients/zbctl:**
  *  implement key based commands ([859f13f7](https://github.com/zeebe-io/zeebe/commit/859f13f70f08b856bef65a0e41f4fcd120aa89c0))
  *  add command to generate shell completion ([4d0163d9](https://github.com/zeebe-io/zeebe/commit/4d0163d928fd1b47e61ec5ab206dfe5a6df99171))
  *  Added zbctl with create instance, job, deploy and status commands ([fe9672a6](https://github.com/zeebe-io/zeebe/commit/fe9672a608e7872f028db55502934cef9d61f422))
* **dist:**  add new gossip config prop ([a5dc8447](https://github.com/zeebe-io/zeebe/commit/a5dc844797fa7ad93f69013472d9004ceed06eb9))
* **documentation:**
  *  parallel gateway ([121446b9](https://github.com/zeebe-io/zeebe/commit/121446b9919753f6dd93e26d9babbf22a55f7e47))
  *  overview of BPMN coverage ([2d11dcf6](https://github.com/zeebe-io/zeebe/commit/2d11dcf626b403de4bfd1b4c354d0952bad3fdf5))
  *  BPMN sub process ([ce73e00a](https://github.com/zeebe-io/zeebe/commit/ce73e00a08762a99fbdda3f586745af85b5e720b))
* **exporters:**
  *  add elasticsearch exporter ([12a70dbd](https://github.com/zeebe-io/zeebe/commit/12a70dbd464214fb9e36d222989305362874ba8c))
  *  adds stream processor to export records ([3752034a](https://github.com/zeebe-io/zeebe/commit/3752034af9368e66326d2d7368a79534ffab9b6a))
  *  load, configure, and validate exporters ([07bff107](https://github.com/zeebe-io/zeebe/commit/07bff10739a2d6980777519e6e67b04a70cbcac1))
  *  introduce new zb-exporter module ([0aab5e77](https://github.com/zeebe-io/zeebe/commit/0aab5e77813fb82757bc177f5eff35c792698387))
  *  adds exporter documentation ([9a8a9a0b](https://github.com/zeebe-io/zeebe/commit/9a8a9a0b0bc1e0a29fbf34e8d8d84a32a9d981ae))
  *  add AssertJ asserts for exporter records ([c16d3341](https://github.com/zeebe-io/zeebe/commit/c16d33418ea70c8ef981559be9a2136ce24bffff))
* **gateway:**
  *  implement activate jobs rpc call ([06f4e463](https://github.com/zeebe-io/zeebe/commit/06f4e463efd3ca1be67056660f12abd4ad7867d8))
  *  implement list workflows and get worklow ([0e84e7e4](https://github.com/zeebe-io/zeebe/commit/0e84e7e4a5386c8707120dc0b2227bcb82b769d2))
  *  cancel workflow instance ([e1d0b6ca](https://github.com/zeebe-io/zeebe/commit/e1d0b6ca779e5737c3e4145b4f12725536008da7))
  *  deploy workflow on java and go ([e5afa87a](https://github.com/zeebe-io/zeebe/commit/e5afa87a7fdb15febc470f9bc51d0399daa2de62))
  *  Added all needed infrastructure for gateway ([0ab54eba](https://github.com/zeebe-io/zeebe/commit/0ab54eba82d6e9987ecce11d1cea5c825437da23))
  *  expose scope instance key ([a8954fa4](https://github.com/zeebe-io/zeebe/commit/a8954fa439cb062acc65411296d94d588e510163))
  *  publish a message via Java Client ([96f4f84e](https://github.com/zeebe-io/zeebe/commit/96f4f84ee3c0a49e4350888ae359645566b4485f))
* **gateway-protocol:**  add ListWorkflows and GetWorkflow methods ([ffdc1098](https://github.com/zeebe-io/zeebe/commit/ffdc109897af4b4e42dfe09ad77812e5a09d1fac))
* **gossip:**  send sync request repeatedly ([ae9373ee](https://github.com/zeebe-io/zeebe/commit/ae9373ee85e9d7c4824ca5560bb8bbeb77602a2d))
* **json-path:**
  *  non-strict mapping extraction ([a116e60f](https://github.com/zeebe-io/zeebe/commit/a116e60fce82a18da75fd2c2d6667641ee878d5b))
  *  new mapping type to collect a result in an array ([eadd29c1](https://github.com/zeebe-io/zeebe/commit/eadd29c125f83661993c88351a1bc6cfa1604681))
* **logstreams:**  expose rocksb internal api ([07df1302](https://github.com/zeebe-io/zeebe/commit/07df130275641a7b6cdb1035878671fbcc312bf0))
* **msg-pack:**  merge multiple documents ([504ce125](https://github.com/zeebe-io/zeebe/commit/504ce125dc921c0f30ada33aa66b1208689dd93e))
* **transport:**  add endpoint registry ([b24b6ff5](https://github.com/zeebe-io/zeebe/commit/b24b6ff5c5d42a8801c008fbb4e52982c4fe30ac))
